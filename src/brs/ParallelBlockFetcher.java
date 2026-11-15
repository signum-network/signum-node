package brs;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import brs.peer.Peer;
import brs.util.JSON;

/**
 * Downloads consecutive height ranges ("segments") from several peers at the same time during bulk
 * sync. Segment 0 comes from the feeder peer, segment i from helper candidate i, which must first
 * report a higher cumulative difficulty than ours and a height that covers its segment.
 * <p>
 * The segments are returned in height order and cut at the first gap: a missing, empty, late or
 * unqualified segment cuts the result before it, a short segment cuts it after it. Only a peer
 * that sends more blocks than requested is blacklisted; slow or silent peers are not. The caller
 * still checks that the blocks link up and verifies them as usual.
 */
final class ParallelBlockFetcher {

    private static final Logger logger = LoggerFactory.getLogger(ParallelBlockFetcher.class);

    /** The default of the serving node; keeps a single response small. */
    static final int MAX_BLOCKS_PER_PEER = 100;

    /** Blocks downloaded from one peer, starting after the block at {@code fromHeight}. */
    record Segment(Peer peer, int fromHeight, JsonArray blocks) {
    }

    private final DownloadPeers downloadPeers;
    private final int peers;
    private final int blocksPerPeer;
    private final long timeoutMs;
    private final ExecutorService executor;

    ParallelBlockFetcher(DownloadPeers downloadPeers, int peers, int blocksPerPeer, long timeoutMs) {
        this.downloadPeers = downloadPeers;
        this.peers = Math.max(1, peers);
        this.blocksPerPeer = Math.max(1, Math.min(MAX_BLOCKS_PER_PEER, blocksPerPeer));
        this.timeoutMs = timeoutMs;
        this.executor = this.peers > 1 ? Executors.newFixedThreadPool(this.peers, daemonThreads()) : null;
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
        if (executor == null) {
            return List.of();
        }
        int total = Math.min(Math.min(peers * blocksPerPeer, feederHeight - cacheHeight), maxBlocks);
        if (total <= 0) {
            return List.of();
        }
        int wantedSegments = (total + blocksPerPeer - 1) / blocksPerPeer;
        List<Peer> helpers = pickHelpers(feeder, wantedSegments - 1);
        if (helpers.isEmpty()) {
            return List.of();
        }

        long start = System.nanoTime();
        List<Peer> segmentPeers = new ArrayList<>();
        segmentPeers.add(feeder);
        segmentPeers.addAll(helpers);
        List<Future<JsonArray>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < segmentPeers.size(); i++) {
                futures.add(executor.submit(segmentTask(i, segmentPeers.get(i), ourCumulativeDifficulty,
                        cacheHeight, total)));
            }
            List<Segment> segments = collect(futures, segmentPeers, cacheHeight, total, start);
            logRound(segments, start);
            return segments;
        } catch (RejectedExecutionException e) {
            // shutting down
            return List.of();
        } finally {
            futures.forEach(future -> future.cancel(true));
        }
    }

    void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private List<Peer> pickHelpers(Peer feeder, int max) {
        if (max <= 0) {
            return List.of();
        }
        List<Peer> helpers = new ArrayList<>(downloadPeers.candidates());
        helpers.remove(feeder);
        Collections.shuffle(helpers);
        return helpers.subList(0, Math.min(max, helpers.size()));
    }

    private Callable<JsonArray> segmentTask(int index, Peer peer, BigInteger ourCumulativeDifficulty,
            int cacheHeight, int total) {
        int fromHeight = fromHeight(index, cacheHeight);
        int count = count(index, total);
        if (index == 0) {
            return () -> requestBlocks(peer, fromHeight, count);
        }
        return () -> qualifies(peer, ourCumulativeDifficulty, fromHeight + count)
                ? requestBlocks(peer, fromHeight, count)
                : null;
    }

    private List<Segment> collect(List<Future<JsonArray>> futures, List<Peer> segmentPeers, int cacheHeight,
            int total, long start) throws InterruptedException {
        long deadline = start + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        List<Segment> segments = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            Peer peer = segmentPeers.get(i);
            int fromHeight = fromHeight(i, cacheHeight);
            int count = count(i, total);
            JsonArray blocks = await(futures.get(i), deadline, peer, fromHeight);
            if (blocks == null || blocks.isEmpty()) {
                logger.debug("Parallel download: no blocks from {} after height {}, cutting the round",
                        peer.getPeerAddress(), fromHeight);
                break;
            }
            if (blocks.size() > count) {
                peer.blacklist("sends too many blocks");
                break;
            }
            segments.add(new Segment(peer, fromHeight, blocks));
            if (blocks.size() < count) {
                logger.debug("Parallel download: {} sent {} of {} blocks after height {}, cutting the round",
                        peer.getPeerAddress(), blocks.size(), count, fromHeight);
                break;
            }
        }
        return segments;
    }

    private static JsonArray await(Future<JsonArray> future, long deadline, Peer peer, int fromHeight)
            throws InterruptedException {
        try {
            return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            logger.debug("Parallel download: {} timed out after height {}", peer.getPeerAddress(), fromHeight);
        } catch (ExecutionException e) {
            logger.debug("Parallel download: request to {} after height {} failed", peer.getPeerAddress(),
                    fromHeight, e.getCause());
        }
        return null;
    }

    private static boolean qualifies(Peer peer, BigInteger ourCumulativeDifficulty, int segmentEndHeight) {
        JsonObject request = new JsonObject();
        request.addProperty("requestType", "getCumulativeDifficulty");
        JsonObject response = peer.send(JSON.prepareRequest(request));
        if (response == null) {
            return false;
        }
        String difficulty = JSON.getAsString(response.get("cumulativeDifficulty"));
        JsonElement height = response.get("blockchainHeight");
        if (difficulty == null || height == null) {
            return false;
        }
        try {
            return new BigInteger(difficulty).compareTo(ourCumulativeDifficulty) > 0
                    && JSON.getAsInt(height) >= segmentEndHeight;
        } catch (NumberFormatException e) {
            return false;
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

    private static java.util.concurrent.ThreadFactory daemonThreads() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "ParallelBlockFetcher-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
