package brs;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import brs.peer.Peer;
import brs.util.JSON;

/**
 * Downloads consecutive height ranges ("segments") from several peers at the same time during bulk
 * sync. Segment 0 comes from the feeder peer and is downloaded on the calling thread, so it never
 * waits behind helper requests. Each helper candidate runs on the pool: it first checks that it
 * reports a higher cumulative difficulty than ours and a height that covers the whole round, and
 * only then takes the next free segment. Helpers that do not qualify therefore leave no gap.
 * <p>
 * The segments are returned in height order and cut at the first gap: a missing, empty or late
 * segment cuts the result before it, a short segment cuts it after it. Only a peer that sends more
 * blocks than requested is blacklisted; slow or silent peers are not. The caller still checks that
 * the blocks link up and verifies them as usual.
 */
final class ParallelBlockFetcher {

    private static final Logger logger = LoggerFactory.getLogger(ParallelBlockFetcher.class);

    /** The default of the serving node; keeps a single response small. */
    static final int MAX_BLOCKS_PER_PEER = 100;

    /** Upper bound for the peers per round, so a configuration typo cannot start hundreds of threads. */
    static final int MAX_PEERS = 16;

    /**
     * Minimum time helpers get after the feeder's segment has arrived. On mainnet a helper needs about
     * 0.5 s for a probe and 1.4 s for a segment of 100 blocks.
     */
    static final long MIN_HELPER_GRACE_MS = 3_000;

    /** How long a helper's probe result (cumulative difficulty and height) is reused. */
    static final long PROBE_TTL_MS = 60_000;

    /** Blocks downloaded from one peer, starting after the block at {@code fromHeight}. */
    record Segment(Peer peer, int fromHeight, JsonArray blocks) {
    }

    /** The shared state of one round's helper tasks. */
    private static final class HelperRound {
        final List<CompletableFuture<Claim>> slots = new ArrayList<>();
        final AtomicReferenceArray<Peer> claimants;
        final Set<Peer> probing = ConcurrentHashMap.newKeySet();
        final AtomicInteger claimed = new AtomicInteger();
        final AtomicInteger probed = new AtomicInteger();

        HelperRound(int helpers) {
            for (int i = 0; i < helpers; i++) {
                slots.add(new CompletableFuture<>());
            }
            claimants = new AtomicReferenceArray<>(helpers);
        }
    }

    /** A helper's answer to {@code getCumulativeDifficulty}, reused for {@link #PROBE_TTL_MS}. */
    private record ProbeResult(BigInteger cumulativeDifficulty, int height, long probedAtNanos) {
    }

    /** What a helper downloaded for the segment it took; {@code blocks} is null if the request failed. */
    private record Claim(Peer peer, JsonArray blocks) {
    }

    private final DownloadPeers downloadPeers;
    private final int peers;
    private final int blocksPerPeer;
    private final long timeoutMs;
    private final ThreadPoolExecutor executor;
    private final long probeTtlNanos;
    private final LongSupplier nanoClock;
    private final long minHelperGraceNanos;
    private final Map<Peer, ProbeResult> probes = new ConcurrentHashMap<>();
    /** Helpers that failed or were too slow, with the clock time until which they are not picked. */
    private final Map<Peer, Long> skippedUntil = new ConcurrentHashMap<>();
    /** The round in progress, so a shutdown can end its wait for helpers right away. */
    private volatile HelperRound currentRound;

    ParallelBlockFetcher(DownloadPeers downloadPeers, int peers, int blocksPerPeer, long timeoutMs) {
        this(downloadPeers, peers, blocksPerPeer, timeoutMs, PROBE_TTL_MS, MIN_HELPER_GRACE_MS, System::nanoTime);
    }

    ParallelBlockFetcher(DownloadPeers downloadPeers, int peers, int blocksPerPeer, long timeoutMs, long probeTtlMs,
            long minHelperGraceMs, LongSupplier nanoClock) {
        this.minHelperGraceNanos = TimeUnit.MILLISECONDS.toNanos(minHelperGraceMs);
        this.probeTtlNanos = TimeUnit.MILLISECONDS.toNanos(probeTtlMs);
        this.nanoClock = nanoClock;
        this.downloadPeers = downloadPeers;
        this.peers = Math.max(1, Math.min(MAX_PEERS, peers));
        this.blocksPerPeer = Math.max(1, Math.min(MAX_BLOCKS_PER_PEER, blocksPerPeer));
        this.timeoutMs = timeoutMs;
        // the feeder's segment runs on the calling thread, the helpers on the pool
        this.executor = this.peers > 1 ? startThreads(this.peers - 1) : null;
    }

    int blocksPerPeer() {
        return blocksPerPeer;
    }

    /**
     * @return true if a round should use the parallel download: we extend our chain linearly and
     *     the feeder is far enough ahead for at least two segments
     */
    boolean isApplicable(boolean saveInCache, int cacheHeight, int feederHeight) {
        return peers > 1 && saveInCache && feederHeight >= cacheHeight + 2 * blocksPerPeer;
    }

    /**
     * @return how many blocks the download cache may still grow ahead of the chain: half the
     *     rollback window after PoC+, unlimited before
     */
    static int maxBlocksAhead(boolean pocPlus, int chainHeight, int cacheHeight) {
        return pocPlus ? chainHeight + Constants.MAX_ROLLBACK / 2 - cacheHeight : Integer.MAX_VALUE;
    }

    /**
     * Downloads up to {@code peers × blocksPerPeer} blocks after {@code cacheHeight}.
     *
     * @return the segments in height order, possibly fewer than requested; empty if there is no
     *     helper candidate or nothing may be downloaded
     */
    List<Segment> fetch(Peer feeder, BigInteger ourCumulativeDifficulty, int cacheHeight, int feederHeight,
            int maxBlocks) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (executor == null) {
            return List.of();
        }
        probes.values().removeIf(this::isStale);
        long now = nanoClock.getAsLong();
        skippedUntil.values().removeIf(until -> until - now < 0);
        int total = Math.min(Math.min(peers * blocksPerPeer, feederHeight - cacheHeight), maxBlocks);
        if (total <= 0) {
            return List.of();
        }
        List<Peer> helpers = pickHelpers(feeder, (total + blocksPerPeer - 1) / blocksPerPeer - 1,
                ourCumulativeDifficulty, cacheHeight + total);
        if (helpers.isEmpty()) {
            return List.of();
        }
        int roundTotal = Math.min(total, (1 + helpers.size()) * blocksPerPeer);

        long start = System.nanoTime();
        HelperRound round = new HelperRound(helpers.size());
        currentRound = round;
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (Peer helper : helpers) {
                tasks.add(executor.submit(() -> runHelper(helper, ourCumulativeDifficulty, cacheHeight, roundTotal,
                        round)));
            }
            JsonArray feederBlocks = requestBlocks(feeder, cacheHeight, count(0, roundTotal));
            List<Segment> segments = collect(feeder, feederBlocks, round, cacheHeight, roundTotal, start);
            logRound(segments, start);
            return segments;
        } catch (RejectedExecutionException e) {
            // shutting down
            return List.of();
        } finally {
            currentRound = null;
            tasks.forEach(task -> task.cancel(true));
            executor.purge();
        }
    }

    /**
     * Skips a helper whose segment did not link to the previous one, for the probe TTL. It may be
     * on another fork, so it is not blacklisted.
     */
    void skipHelper(Peer helper) {
        skip(helper);
    }

    /** Stops the helper threads and ends the wait of a round in progress. */
    void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        HelperRound round = currentRound;
        if (round != null) {
            round.slots.forEach(slot -> slot.complete(null));
        }
    }

    /**
     * Picks up to {@code max} random candidates, leaving out skipped peers and peers whose fresh probe
     * result already shows they cannot serve the round. If none is left, the caller falls back to the
     * single-peer download, which fetches more blocks per round than the feeder's segment alone.
     */
    private List<Peer> pickHelpers(Peer feeder, int max, BigInteger ourCumulativeDifficulty, int roundEndHeight) {
        if (max <= 0) {
            return List.of();
        }
        List<Peer> helpers = new ArrayList<>(downloadPeers.candidates());
        helpers.remove(feeder);
        helpers.removeIf(skippedUntil::containsKey);
        helpers.removeIf(helper -> isKnownUnqualified(helper, ourCumulativeDifficulty, roundEndHeight));
        Collections.shuffle(helpers);
        return helpers.subList(0, Math.min(max, helpers.size()));
    }

    /**
     * Probes one helper and, if it qualifies, downloads the next free segment. The last helper to
     * finish its probe marks the segments nobody took as missing.
     */
    private void runHelper(Peer helper, BigInteger ourCumulativeDifficulty, int cacheHeight, int roundTotal,
            HelperRound round) {
        boolean qualified;
        round.probing.add(helper);
        try {
            qualified = qualifies(helper, ourCumulativeDifficulty, cacheHeight + roundTotal);
        } catch (RuntimeException e) {
            skip(helper);
            qualified = false;
        }
        int slot = qualified ? round.claimed.getAndIncrement() : -1;
        if (slot >= 0) {
            round.claimants.set(slot, helper);
        }
        round.probing.remove(helper);
        if (round.probed.incrementAndGet() == round.slots.size()) {
            for (int i = round.claimed.get(); i < round.slots.size(); i++) {
                round.slots.get(i).complete(null);
            }
        }
        if (slot < 0) {
            return;
        }
        JsonArray blocks = null;
        try {
            blocks = requestBlocks(helper, fromHeight(slot + 1, cacheHeight), count(slot + 1, roundTotal));
        } catch (RuntimeException e) {
            logger.debug("Parallel download: request to {} failed", helper.getPeerAddress(), e);
        }
        if (blocks == null || blocks.isEmpty()) {
            // it may have dropped behind or gone away
            skip(helper);
        }
        round.slots.get(slot).complete(new Claim(helper, blocks));
    }

    private List<Segment> collect(Peer feeder, JsonArray feederBlocks, HelperRound round, int cacheHeight,
            int roundTotal, long start) throws InterruptedException {
        List<Segment> segments = new ArrayList<>();
        if (!accept(feeder, cacheHeight, feederBlocks, count(0, roundTotal), segments)) {
            return segments;
        }
        long deadline = helperDeadline(start, System.nanoTime());
        for (int i = 0; i < round.slots.size(); i++) {
            int fromHeight = fromHeight(i + 1, cacheHeight);
            Claim claim = await(round.slots.get(i), deadline);
            if (!round.slots.get(i).isDone()) {
                skipLateHelpers(round, i);
            }
            if (claim == null) {
                logger.debug("Parallel download: no helper delivered the segment after height {}, cutting the round",
                        fromHeight);
                break;
            }
            if (!accept(claim.peer(), fromHeight, claim.blocks(), count(i + 1, roundTotal), segments)) {
                break;
            }
        }
        return segments;
    }

    /**
     * Helpers may take twice as long as the feeder, but at least {@link #MIN_HELPER_GRACE_MS} after
     * its segment arrived, and never longer than the round timeout.
     */
    private long helperDeadline(long start, long feederDone) {
        long grace = Math.max(2 * (feederDone - start), minHelperGraceNanos);
        return Math.min(start + TimeUnit.MILLISECONDS.toNanos(timeoutMs), feederDone + grace);
    }

    /** Skips the helper that holds the late segment and every helper still probing. */
    private void skipLateHelpers(HelperRound round, int slot) {
        Peer claimant = round.claimants.get(slot);
        if (claimant != null) {
            skip(claimant);
        }
        round.probing.forEach(this::skip);
    }

    private void skip(Peer peer) {
        probes.remove(peer);
        skippedUntil.put(peer, nanoClock.getAsLong() + probeTtlNanos);
        logger.debug("Parallel download: skipping helper {} for {} s", peer.getPeerAddress(),
                TimeUnit.NANOSECONDS.toSeconds(probeTtlNanos));
    }

    /** Adds the segment if it is usable. @return true if the next segment may follow it */
    private static boolean accept(Peer peer, int fromHeight, JsonArray blocks, int count, List<Segment> segments) {
        if (blocks == null || blocks.isEmpty()) {
            logger.debug("Parallel download: no blocks from {} after height {}, cutting the round",
                    peer.getPeerAddress(), fromHeight);
            return false;
        }
        if (blocks.size() > count) {
            peer.blacklist("sends too many blocks");
            return false;
        }
        segments.add(new Segment(peer, fromHeight, blocks));
        if (blocks.size() < count) {
            logger.debug("Parallel download: {} sent {} of {} blocks after height {}, cutting the round",
                    peer.getPeerAddress(), blocks.size(), count, fromHeight);
            return false;
        }
        return true;
    }

    private static Claim await(CompletableFuture<Claim> slot, long deadline) throws InterruptedException {
        try {
            return slot.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException | ExecutionException e) {
            return null;
        }
    }

    /**
     * @return true if the helper reports a higher cumulative difficulty than ours and a height that
     *     covers the round. Reuses a fresh probe result.
     */
    private boolean qualifies(Peer peer, BigInteger ourCumulativeDifficulty, int roundEndHeight) {
        ProbeResult result = probes.get(peer);
        if (result == null || isStale(result)) {
            result = probe(peer);
            if (result == null) {
                skip(peer);
                return false;
            }
            probes.put(peer, result);
        }
        return result.cumulativeDifficulty().compareTo(ourCumulativeDifficulty) > 0
                && result.height() >= roundEndHeight;
    }

    private boolean isKnownUnqualified(Peer helper, BigInteger ourCumulativeDifficulty, int roundEndHeight) {
        ProbeResult result = probes.get(helper);
        return result != null && !isStale(result)
                && (result.cumulativeDifficulty().compareTo(ourCumulativeDifficulty) <= 0
                        || result.height() < roundEndHeight);
    }

    private boolean isStale(ProbeResult result) {
        return nanoClock.getAsLong() - result.probedAtNanos() > probeTtlNanos;
    }

    /** @return the helper's cumulative difficulty and height, or null if it gave no usable answer */
    private ProbeResult probe(Peer peer) {
        JsonObject request = new JsonObject();
        request.addProperty("requestType", "getCumulativeDifficulty");
        JsonObject response = peer.send(JSON.prepareRequest(request));
        if (response == null) {
            return null;
        }
        String difficulty = JSON.getAsString(response.get("cumulativeDifficulty"));
        JsonElement height = response.get("blockchainHeight");
        if (difficulty == null || height == null) {
            return null;
        }
        try {
            return new ProbeResult(new BigInteger(difficulty), JSON.getAsInt(height), nanoClock.getAsLong());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static JsonArray requestBlocks(Peer peer, int fromHeight, int count) {
        JsonObject request = new JsonObject();
        request.addProperty("requestType", "getBlocksFromHeight");
        request.addProperty("height", fromHeight);
        request.addProperty("numBlocks", count);
        JsonObject response = peer.send(JSON.prepareRequest(request));
        if (response == null || !response.has("nextBlocks") || !response.get("nextBlocks").isJsonArray()) {
            return null;
        }
        return response.getAsJsonArray("nextBlocks");
    }

    private int fromHeight(int index, int cacheHeight) {
        return cacheHeight + index * blocksPerPeer;
    }

    private int count(int index, int total) {
        return Math.min(blocksPerPeer, total - index * blocksPerPeer);
    }

    private static void logRound(List<Segment> segments, long start) {
        if (logger.isDebugEnabled()) {
            int blocks = segments.stream().mapToInt(segment -> segment.blocks().size()).sum();
            logger.debug("Parallel download: {} segments, {} blocks from {} in {} ms", segments.size(), blocks,
                    segments.stream().map(segment -> segment.peer().getPeerAddress()).toList(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
    }

    /** A fixed pool of daemon threads, started right away so a sync round does not wait for them. */
    private static ThreadPoolExecutor startThreads(int count) {
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(count, count, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "ParallelBlockFetcher-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        pool.prestartAllCoreThreads();
        return pool;
    }
}
