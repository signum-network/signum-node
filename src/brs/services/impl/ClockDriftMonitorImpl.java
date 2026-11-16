package brs.services.impl;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brs.Constants;
import brs.services.ClockDriftMonitor;
import brs.services.TimeService;

/**
 * Estimates the local clock offset as the median of {@code arrival time - block timestamp} over the
 * last {@link #WINDOW_SIZE} fresh blocks. The median keeps single forgers with a bad clock from
 * moving the estimate.
 * <p>
 * A sample is taken when a block is pushed, so only validated blocks count, but it uses the time
 * the block first arrived. A clock that is behind makes the node reject fresh blocks as "from the
 * future", pop off their parents and push both again later; the first arrivals still give the right
 * offset. Arrivals are recorded per block id for every block parsed from a peer, whether or not it
 * enters the download cache, so a fake block announced first cannot delay the honest one's arrival.
 * <p>
 * Only blocks that arrived in real time count. A block counts if it follows the previous pushed
 * block and arrived about as long after it as their timestamps are apart. During a bulk sync, old
 * blocks arrive far faster than their timestamps advance, so they are skipped. The check compares
 * differences only, so it works however far the local clock is off.
 * <p>
 * Next to that, it keeps the latest clock offset measured against each peer and reports their
 * median once {@link #MIN_PEER_SAMPLES} peers have a recent one. A single peer with a bad clock, or
 * a few lying ones, cannot move the median.
 */
public class ClockDriftMonitorImpl implements ClockDriftMonitor {

    static final int WINDOW_SIZE = 15;
    static final int MIN_SAMPLES = 5;
    /** How much earlier or later than its timestamp suggests a block may arrive and still count. */
    static final int ARRIVAL_TOLERANCE_SECONDS = 30;
    static final int WARN_INTERVAL_SECONDS = 600;
    /**
     * First arrivals kept, oldest dropped first, so blocks that never get pushed cannot grow it. Enough
     * for the tip blocks and the blocks a pop-off pushes again. Losing an entry (bulk sync far ahead,
     * a flood of junk blocks) only costs a sample: a late arrival recorded again fails the
     * real-time check.
     */
    static final int MAX_TRACKED_ARRIVALS = 2 * Constants.MAX_ROLLBACK;
    /** Peers re-measure hourly; a sample older than two rounds is stale. */
    static final int PEER_SAMPLE_MAX_AGE_SECONDS = 7200;
    static final int MIN_PEER_SAMPLES = 3;
    /** Peers kept, least recently measured dropped first, so rotating addresses cannot grow it. */
    static final int MAX_PEER_SAMPLES = 256;

    static final String SOURCE_BLOCKS = "blocks";
    static final String SOURCE_PEERS = "peers";

    private static final Logger logger = LoggerFactory.getLogger(ClockDriftMonitorImpl.class);

    /** Receives the operator notifications; production writes them to the log. */
    interface DriftLog {
        void warn(String source, int offsetSeconds);

        void recovered(int offsetSeconds);
    }

    private final TimeService timeService;
    private final int thresholdSeconds;
    private final DriftLog driftLog;

    private final Deque<Integer> samples = new ArrayDeque<>(WINDOW_SIZE);
    private final Map<Long, Arrival> firstArrivals = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Arrival> eldest) {
            return size() > MAX_TRACKED_ARRIVALS;
        }
    };
    private final Map<String, PeerSample> peerSamples = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PeerSample> eldest) {
            return size() > MAX_PEER_SAMPLES;
        }
    };
    private boolean hasReference;
    private int lastHeight;
    private int lastTimestamp;
    private int lastArrival;

    private boolean warned;
    private int lastWarnTime;

    public ClockDriftMonitorImpl(TimeService timeService, int thresholdSeconds) {
        this(timeService, thresholdSeconds, new LoggingDriftLog());
    }

    ClockDriftMonitorImpl(TimeService timeService, int thresholdSeconds, DriftLog driftLog) {
        this.timeService = timeService;
        this.thresholdSeconds = Math.max(0, thresholdSeconds);
        this.driftLog = driftLog;
    }

    @Override
    public synchronized void onBlockReceived(long blockId) {
        firstArrivals.putIfAbsent(blockId, new Arrival(timeService.getEpochTime()));
    }

    @Override
    public synchronized void onBlockPushed(long blockId, int height, int blockTimestamp) {
        // kept after the push: a popped-off block pushed again must keep its first arrival
        Arrival firstArrival = firstArrivals.get(blockId);
        if (firstArrival == null) {
            // a block this node forged itself was never received: it sets the reference, but its
            // timestamp comes from the local clock, so it never becomes a sample, even when a
            // peer echoes it back and it is pushed again after a pop-off
            firstArrival = new Arrival(timeService.getEpochTime());
            firstArrival.sampled = true;
            firstArrivals.put(blockId, firstArrival);
        }
        int arrival = firstArrival.time;
        // a height other than the next one (pop-off, reorg) only resets the reference; a block
        // pushed again after a pop-off was sampled already
        if (!firstArrival.sampled && hasReference && height == lastHeight + 1
                && arrivedInRealTime(blockTimestamp, arrival)) {
            firstArrival.sampled = true;
            addSample(saturatedInt((long) arrival - blockTimestamp));
            reportSafely(timeService.getEpochTime());
        }
        hasReference = true;
        lastHeight = height;
        lastTimestamp = blockTimestamp;
        lastArrival = arrival;
    }

    /**
     * The block arrived about as long after the reference as their timestamps are apart: not early
     * (bulk sync) and not late (missed announcement, arrival recorded again). Valid chains have
     * strictly increasing timestamps; the check relies on a positive gap.
     */
    private boolean arrivedInRealTime(int blockTimestamp, int arrival) {
        long timestampGap = (long) blockTimestamp - lastTimestamp;
        long arrivalGap = (long) arrival - lastArrival;
        return timestampGap > 0
                && Math.abs(arrivalGap - timestampGap) <= ARRIVAL_TOLERANCE_SECONDS
                && 2 * arrivalGap >= timestampGap;
    }

    private static int saturatedInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE + 1L, Math.min(Integer.MAX_VALUE, value));
    }

    private void addSample(int offset) {
        if (samples.size() == WINDOW_SIZE) {
            samples.removeFirst();
        }
        samples.addLast(offset);
    }

    /** Runs on block push: a failure of this diagnostic must never get in the way of block processing. */
    private void reportSafely(int now) {
        try {
            report(now);
        } catch (RuntimeException e) {
            logger.debug("Clock drift report failed", e);
        }
    }

    private void report(int now) {
        OptionalInt blockOffset = getEstimatedOffsetSeconds();
        OptionalInt peerOffset = currentPeerOffsetSeconds(now);
        boolean blocksBeyond = isBeyondThreshold(blockOffset);
        boolean peersBeyond = isBeyondThreshold(peerOffset);
        if (blocksBeyond || peersBeyond) {
            if (!warned || now - lastWarnTime >= WARN_INTERVAL_SECONDS) {
                if (blocksBeyond) {
                    driftLog.warn(SOURCE_BLOCKS, blockOffset.getAsInt());
                }
                if (peersBeyond) {
                    driftLog.warn(SOURCE_PEERS, peerOffset.getAsInt());
                }
                warned = true;
                lastWarnTime = now;
            }
        } else if (warned && (blockOffset.isPresent() || peerOffset.isPresent())) {
            // an estimate that went away (expired peer samples) is not a recovery
            driftLog.recovered(blockOffset.isPresent() ? blockOffset.getAsInt() : peerOffset.getAsInt());
            warned = false;
        }
    }

    @Override
    public synchronized void onPeerClockSample(String peerAddress, long offsetMillis) {
        int now = timeService.getEpochTime();
        // remove first, so an updated peer moves to the end and the least recently measured goes first
        peerSamples.remove(peerAddress);
        peerSamples.put(peerAddress, new PeerSample(offsetMillis, now));
        reportSafely(now);
    }

    @Override
    public synchronized OptionalInt getPeerOffsetSeconds() {
        return currentPeerOffsetSeconds(timeService.getEpochTime());
    }

    @Override
    public synchronized int getPeerSampleCount() {
        dropExpiredPeerSamples(timeService.getEpochTime());
        return peerSamples.size();
    }

    private OptionalInt currentPeerOffsetSeconds(int now) {
        dropExpiredPeerSamples(now);
        if (peerSamples.size() < MIN_PEER_SAMPLES) {
            return OptionalInt.empty();
        }
        long[] sorted = peerSamples.values().stream().mapToLong(sample -> sample.offsetMillis).sorted().toArray();
        int middle = sorted.length / 2;
        long medianMillis = sorted.length % 2 == 1 ? sorted[middle] : floorMean(sorted[middle - 1], sorted[middle]);
        return OptionalInt.of(saturatedInt(medianMillis / 1000));
    }

    /** The mean of two longs, rounded down, without overflow. */
    private static long floorMean(long a, long b) {
        return (a >> 1) + (b >> 1) + (a & b & 1);
    }

    private void dropExpiredPeerSamples(int now) {
        peerSamples.values().removeIf(sample -> (long) now - sample.receivedAt > PEER_SAMPLE_MAX_AGE_SECONDS);
    }

    @Override
    public synchronized OptionalInt getEstimatedOffsetSeconds() {
        if (samples.size() < MIN_SAMPLES) {
            return OptionalInt.empty();
        }
        int[] sorted = samples.stream().mapToInt(Integer::intValue).sorted().toArray();
        int middle = sorted.length / 2;
        if (sorted.length % 2 == 1) {
            return OptionalInt.of(sorted[middle]);
        }
        return OptionalInt.of((int) Math.floorDiv((long) sorted[middle - 1] + sorted[middle], 2));
    }

    @Override
    public synchronized boolean isDrifting() {
        return isBeyondThreshold(getEstimatedOffsetSeconds()) || isBeyondThreshold(getPeerOffsetSeconds());
    }

    private boolean isBeyondThreshold(OptionalInt offset) {
        return thresholdSeconds > 0 && offset.isPresent() && Math.abs(offset.getAsInt()) > thresholdSeconds;
    }

    @Override
    public int getThresholdSeconds() {
        return thresholdSeconds;
    }

    private static final class Arrival {
        private final int time;
        private boolean sampled;

        private Arrival(int time) {
            this.time = time;
        }
    }

    private static final class PeerSample {
        private final long offsetMillis;
        private final int receivedAt;

        private PeerSample(long offsetMillis, int receivedAt) {
            this.offsetMillis = offsetMillis;
            this.receivedAt = receivedAt;
        }
    }

    private static final class LoggingDriftLog implements DriftLog {
        private static final Logger logger = LoggerFactory.getLogger(ClockDriftMonitorImpl.class);

        @Override
        public void warn(String source, int offsetSeconds) {
            logger.warn("Local clock seems to be {} by about {} s compared to the network (measured from {}). "
                    + "Blocks may get rejected and forging may suffer. Please enable time synchronization (NTP).",
                    offsetSeconds < 0 ? "behind" : "ahead", Math.abs(offsetSeconds), source);
        }

        @Override
        public void recovered(int offsetSeconds) {
            logger.info("Local clock is back in range (estimated offset {} s)", offsetSeconds);
        }
    }
}
