package brs.services.impl;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.OptionalInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brs.services.ClockDriftMonitor;
import brs.services.TimeService;

/**
 * Estimates the local clock offset as the median of {@code arrival time - block timestamp} over the
 * last {@link #WINDOW_SIZE} fresh blocks. The median keeps single forgers with a bad clock from
 * moving the estimate.
 * <p>
 * Only blocks that arrive in real time count. A block counts if it follows the previous block seen
 * and arrived about as long after it as their timestamps are apart. During a bulk sync, old blocks
 * arrive far faster than their timestamps advance, so they are skipped. The check compares
 * differences only, so it works however far the local clock is off.
 */
public class ClockDriftMonitorImpl implements ClockDriftMonitor {

    static final int WINDOW_SIZE = 15;
    static final int MIN_SAMPLES = 5;
    /** How much earlier than its timestamp suggests a block may arrive and still count as fresh. */
    static final int ARRIVAL_TOLERANCE_SECONDS = 30;
    static final int WARN_INTERVAL_SECONDS = 600;

    /** Receives the operator notifications; production writes them to the log. */
    interface DriftLog {
        void warn(int offsetSeconds);

        void recovered(int offsetSeconds);
    }

    private final TimeService timeService;
    private final int thresholdSeconds;
    private final DriftLog driftLog;

    private final Deque<Integer> samples = new ArrayDeque<>(WINDOW_SIZE);
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
    public synchronized void onBlockReceived(int height, int blockTimestamp) {
        if (hasReference && height <= lastHeight) {
            return; // seen before (e.g. re-downloaded after a rejection) or a fork block
        }
        int arrival = timeService.getEpochTime();
        if (hasReference && height == lastHeight + 1 && arrivedInRealTime(blockTimestamp, arrival)) {
            addSample(arrival - blockTimestamp);
            report(arrival);
        }
        hasReference = true;
        lastHeight = height;
        lastTimestamp = blockTimestamp;
        lastArrival = arrival;
    }

    private boolean arrivedInRealTime(int blockTimestamp, int arrival) {
        int timestampGap = blockTimestamp - lastTimestamp;
        int arrivalGap = arrival - lastArrival;
        return arrivalGap >= timestampGap - ARRIVAL_TOLERANCE_SECONDS && 2 * arrivalGap >= timestampGap;
    }

    private void addSample(int offset) {
        if (samples.size() == WINDOW_SIZE) {
            samples.removeFirst();
        }
        samples.addLast(offset);
    }

    private void report(int now) {
        OptionalInt offset = getEstimatedOffsetSeconds();
        if (offset.isEmpty()) {
            return;
        }
        if (isDrifting()) {
            if (!warned || now - lastWarnTime >= WARN_INTERVAL_SECONDS) {
                driftLog.warn(offset.getAsInt());
                warned = true;
                lastWarnTime = now;
            }
        } else if (warned) {
            driftLog.recovered(offset.getAsInt());
            warned = false;
        }
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
        return OptionalInt.of(Math.floorDiv(sorted[middle - 1] + sorted[middle], 2));
    }

    @Override
    public synchronized boolean isDrifting() {
        if (thresholdSeconds == 0) {
            return false;
        }
        OptionalInt offset = getEstimatedOffsetSeconds();
        return offset.isPresent() && Math.abs(offset.getAsInt()) > thresholdSeconds;
    }

    @Override
    public int getThresholdSeconds() {
        return thresholdSeconds;
    }

    private static final class LoggingDriftLog implements DriftLog {
        private static final Logger logger = LoggerFactory.getLogger(ClockDriftMonitorImpl.class);

        @Override
        public void warn(int offsetSeconds) {
            logger.warn("Local clock seems to be {} by about {} s compared to the network. "
                    + "Blocks may get rejected and forging may suffer. Please enable time synchronization (NTP).",
                    offsetSeconds < 0 ? "behind" : "ahead", Math.abs(offsetSeconds));
        }

        @Override
        public void recovered(int offsetSeconds) {
            logger.info("Local clock is back in range (estimated offset {} s)", offsetSeconds);
        }
    }
}
