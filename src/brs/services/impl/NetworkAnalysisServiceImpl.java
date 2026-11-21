package brs.services.impl;

import brs.Block;
import brs.Blockchain;
import brs.RollbackEvent;
import brs.SignumException;
import brs.db.sql.Db;
import brs.peer.Peer;
import brs.peer.Peers;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.NetworkAnalysisService;
import brs.services.impl.PeerBlockProbe.ProbeResult;
import brs.util.Convert;
import brs.services.BlockPeerCheck.Outcome;
import brs.services.BlockPeerCheck;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class NetworkAnalysisServiceImpl implements NetworkAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAnalysisServiceImpl.class);

    private static final Table<Record> FORK_HISTORY = DSL.table("fork_history");
    private static final Field<Long> DETECTED_AT = DSL.field("detected_at", Long.class);
    private static final Field<Integer> ROLLBACK_HEIGHT = DSL.field("rollback_height", Integer.class);
    private static final Field<Integer> ROLLBACK_DEPTH = DSL.field("rollback_depth", Integer.class);
    private static final Field<String> OLD_TOP_BLOCK_ID = DSL.field("old_top_block_id", String.class);
    private static final Field<String> NEW_TOP_BLOCK_ID = DSL.field("new_top_block_id", String.class);
    private static final Field<String> PEER_SOURCE = DSL.field("peer_source", String.class);

    /** Parallel block checks per snapshot, so one slow peer cannot add up all P2P timeouts. */
    private static final int PROBE_THREADS = 8;
    /** Added to the P2P connect + read timeouts to get the deadline for one block check. */
    private static final int PROBE_DEADLINE_MARGIN_MS = 1_000;

    private final Blockchain blockchain;
    private final PropertyService propertyService;
    private final Supplier<Collection<Peer>> connectedPeers;
    private final PeerBlockProbe probe;
    private final ExecutorService probePool;

    private final AtomicReference<JsonObject> cachedStatus = new AtomicReference<>(null);
    private final Object refreshLock = new Object();
    private final ScheduledExecutorService scheduler;
    private final long probeTimeoutMs;
    private final LongSupplier clock;

    /** Blocks asked about: only near the tip, where forks happen (about one day). */
    static final int RECENT_BLOCKS = 360;
    static final long BLOCK_CHECK_CACHE_TTL_MS = 60_000;
    /** Rounds run one at a time and at most one per this gap, so scripted calls cannot keep peers busy. */
    static final long MIN_BLOCK_CHECK_GAP_MS = 10_000;
    private long lastBlockCheckStartedAt = Long.MIN_VALUE;
    private final Map<Long, BlockPeerCheck> blockChecks = new ConcurrentHashMap<>();
    private final Object blockCheckLock = new Object();
    private long runningBlockId;
    private CompletableFuture<BlockPeerCheck> runningBlockCheck;

    public NetworkAnalysisServiceImpl(Blockchain blockchain, PropertyService propertyService) {
        this(blockchain, propertyService, () -> Peers.getPeers(Peer.State.CONNECTED), new P2PPeerBlockProbe(),
                Executors.newSingleThreadScheduledExecutor(
                        r -> { Thread t = new Thread(r, "network-analysis-worker"); t.setDaemon(true); return t; }),
                Executors.newFixedThreadPool(PROBE_THREADS,
                        r -> { Thread t = new Thread(r, "network-analysis-probe"); t.setDaemon(true); return t; }),
                propertyService.getInt(Props.P2P_TIMEOUT_CONNECT_MS) + propertyService.getInt(Props.P2P_TIMEOUT_READ_MS)
                        + PROBE_DEADLINE_MARGIN_MS);
    }

    NetworkAnalysisServiceImpl(Blockchain blockchain, PropertyService propertyService,
            Supplier<Collection<Peer>> connectedPeers, PeerBlockProbe probe,
            ScheduledExecutorService scheduler, ExecutorService probePool, long probeTimeoutMs) {
        this(blockchain, propertyService, connectedPeers, probe, scheduler, probePool, probeTimeoutMs,
                System::currentTimeMillis);
    }

    NetworkAnalysisServiceImpl(Blockchain blockchain, PropertyService propertyService,
            Supplier<Collection<Peer>> connectedPeers, PeerBlockProbe probe,
            ScheduledExecutorService scheduler, ExecutorService probePool, long probeTimeoutMs,
            LongSupplier clock) {
        this.clock = clock;
        this.blockchain = blockchain;
        this.propertyService = propertyService;
        this.connectedPeers = connectedPeers;
        this.probe = probe;
        this.scheduler = scheduler;
        this.probePool = probePool;
        this.probeTimeoutMs = probeTimeoutMs;

        // the endpoints exist only with the web UI enabled: don't probe peers for a snapshot nobody can read
        if (propertyService.getBoolean(Props.WEB_UI_ENABLED)) {
            int intervalSeconds = propertyService.getInt(Props.WEB_UI_NETWORK_STATUS_INTERVAL_SECONDS);
            scheduler.scheduleAtFixedRate(this::refreshNetworkStatus, 30, intervalSeconds, TimeUnit.SECONDS);
        }
        scheduler.scheduleAtFixedRate(this::pruneOldForkHistory, 1, 24, TimeUnit.HOURS);
    }

    @Override
    public JsonObject getNetworkStatus() {
        JsonObject cached = cachedStatus.get();
        if (cached != null) return cached;
        synchronized (refreshLock) {
            // concurrent first callers wait for one check round instead of each starting their own
            if (cachedStatus.get() == null) {
                refreshNetworkStatus();
            }
        }
        return cachedStatus.get();
    }

    @Override
    public List<JsonObject> getForkHistory(int limit) {
        List<JsonObject> result = new ArrayList<>();
        try {
            var records = Db.fetchWithDSLContext(ctx -> ctx
                    .select(DETECTED_AT, ROLLBACK_HEIGHT, ROLLBACK_DEPTH, OLD_TOP_BLOCK_ID, NEW_TOP_BLOCK_ID, PEER_SOURCE)
                    .from(FORK_HISTORY)
                    .orderBy(DETECTED_AT.desc())
                    .limit(Math.min(limit, 200))
                    .fetch());
            for (var r : records) {
                JsonObject entry = new JsonObject();
                entry.addProperty("detectedAt", r.get(DETECTED_AT));
                entry.addProperty("rollbackHeight", r.get(ROLLBACK_HEIGHT));
                entry.addProperty("rollbackDepth", r.get(ROLLBACK_DEPTH));
                entry.addProperty("oldTopBlockId", r.get(OLD_TOP_BLOCK_ID));
                entry.addProperty("newTopBlockId", r.get(NEW_TOP_BLOCK_ID));
                entry.addProperty("peerSource", r.get(PEER_SOURCE));
                result.add(entry);
            }
        } catch (RuntimeException e) {
            logger.error("Error reading fork_history", e);
        }
        return result;
    }

    @Override
    public JsonObject findForkPoint(String peerAddress) {
        Peer peer = lookupPeer(peerAddress);
        JsonObject result = new JsonObject();
        result.addProperty("peer", peerAddress);

        if (peer == null) {
            logger.warn("findForkPoint: peer not found for address={}", peerAddress);
            result.addProperty("error", "Peer not found");
            return result;
        }

        JsonObject cdRequest = new JsonObject();
        cdRequest.addProperty("requestType", "getCumulativeDifficulty");
        JsonObject cdResponse = peer.send(JSON.prepareRequest(cdRequest));
        if (cdResponse == null || cdResponse.has("error")) {
            logger.warn("findForkPoint: cannot reach peer={} via P2P (getCumulativeDifficulty failed), response={}", peerAddress, cdResponse);
            result.addProperty("error", "Cannot reach peer");
            return result;
        }

        int peerHeight = JSON.getAsInt(cdResponse.get("blockchainHeight"));
        int myHeight = blockchain.getHeight();
        int maxLookback = propertyService.getInt(Props.WEB_UI_FORK_POINT_MAX_LOOKBACK);

        logger.debug("findForkPoint: peer={} peerHeight={} myHeight={} maxLookback={}", peerAddress, peerHeight, myHeight, maxLookback);

        int lo = Math.max(1, Math.min(myHeight, peerHeight) - maxLookback);
        int hi = Math.min(myHeight, peerHeight); // include tip: fork may be at our own current height

        // Fast-fail: if chains already disagree at the bottom of our window,
        // the fork is older than maxLookback — no point searching further.
        String ourBlockAtLo = Convert.toUnsignedLong(blockchain.getBlockIdAtHeight(lo));
        String peerBlockAtLo = fetchPeerBlockId(peer, lo);
        logger.debug("findForkPoint: fast-fail check at lo={} ourBlock={} peerBlock={}", lo, ourBlockAtLo, peerBlockAtLo);

        if (peerBlockAtLo == null) {
            logger.warn("findForkPoint: peer={} did not return block at lo={} — P2P fetch failed", peerAddress, lo);
            result.addProperty("error", "Cannot reach peer during fork search");
            return result;
        }
        if (!ourBlockAtLo.equals(peerBlockAtLo)) {
            logger.info("findForkPoint: peer={} fork is older than {} blocks (disagree at lo={})", peerAddress, maxLookback, lo);
            result.addProperty("error", "Fork is older than " + maxLookback + " blocks — peer is on a permanently diverged chain");
            result.addProperty("forkTooOld", true);
            return result;
        }

        // Binary search for the last height where both chains still agree.
        // Invariant: chains agree at lo, may disagree at hi+1.
        // Ceiling mid prevents lo from stalling when hi = lo + 1.
        int steps = 1; // fast-fail check above counts as step 1
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            steps++;

            String ourBlockId = Convert.toUnsignedLong(blockchain.getBlockIdAtHeight(mid));
            String peerBlockId = fetchPeerBlockId(peer, mid);
            logger.debug("findForkPoint: step={} mid={} ourBlock={} peerBlock={}", steps, mid, ourBlockId, peerBlockId);

            if (peerBlockId == null) {
                logger.warn("findForkPoint: peer={} communication error at step={} mid={}", peerAddress, steps, mid);
                result.addProperty("error", "Peer communication error during search");
                return result;
            }

            if (ourBlockId.equals(peerBlockId)) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }

        logger.info("findForkPoint: peer={} fork found at height={} steps={}", peerAddress, lo, steps);
        Block forkBlock = blockchain.getBlockAtHeight(lo);
        result.addProperty("forkAtHeight", lo);
        result.addProperty("forkAtBlockId", forkBlock != null ? forkBlock.getStringId() : "unknown");
        result.addProperty("ourBlockIdAtFork", Convert.toUnsignedLong(blockchain.getBlockIdAtHeight(lo)));
        result.addProperty("searchSteps", steps);
        return result;
    }

    protected Peer lookupPeer(String peerAddress) {
        return Peers.addPeer(peerAddress);
    }

    /**
     * Fetches the block ID at the given height from the peer via the P2P channel.
     *
     * <p>Primary path: {@code getBlocksFromHeight(height, numBlocks=1)} asks the peer
     * for the block immediately after {@code height}. The returned block's
     * {@code previousBlock} field is the peer's block ID at {@code height}.
     *
     * <p>Fallback (peer is at its tip, nothing after {@code height}): fetch the block
     * AT {@code height} via {@code getBlocksFromHeight(height-1, numBlocks=1)} and
     * compute its ID by parsing the block data.
     */
    protected String fetchPeerBlockId(Peer peer, int height) {
        // Primary: get block at height+1 → previousBlock = peer's block ID at height
        JsonObject req = new JsonObject();
        req.addProperty("requestType", "getBlocksFromHeight");
        req.addProperty("height", height);
        req.addProperty("numBlocks", 1);
        JsonObject resp = peer.send(JSON.prepareRequest(req));
        if (resp == null || resp.has("error")) {
            logger.warn("fetchPeerBlockId: height={} getBlocksFromHeight failed (resp={})", height, resp);
            return null;
        }
        JsonArray nextBlocks = JSON.getAsJsonArray(resp.get("nextBlocks"));
        if (nextBlocks != null && !nextBlocks.isEmpty()) {
            String blockId = JSON.getAsString(nextBlocks.get(0).getAsJsonObject().get("previousBlock"));
            logger.debug("fetchPeerBlockId: height={} → {} (via previousBlock)", height, blockId);
            return blockId;
        }

        // Fallback: peer has no block after height (peer is at its tip).
        // Fetch the block at height itself and compute its ID from the parsed block.
        logger.debug("fetchPeerBlockId: height={} peer at tip, falling back to parse block", height);
        JsonObject fallbackReq = new JsonObject();
        fallbackReq.addProperty("requestType", "getBlocksFromHeight");
        fallbackReq.addProperty("height", Math.max(0, height - 1));
        fallbackReq.addProperty("numBlocks", 1);
        JsonObject fallbackResp = peer.send(JSON.prepareRequest(fallbackReq));
        if (fallbackResp == null || fallbackResp.has("error")) {
            logger.warn("fetchPeerBlockId: height={} fallback getBlocksFromHeight failed", height);
            return null;
        }
        JsonArray fallbackBlocks = JSON.getAsJsonArray(fallbackResp.get("nextBlocks"));
        if (fallbackBlocks == null || fallbackBlocks.isEmpty()) {
            logger.debug("fetchPeerBlockId: height={} fallback returned empty nextBlocks", height);
            return null;
        }
        try {
            Block block = Block.parseBlock(fallbackBlocks.get(0).getAsJsonObject(), height);
            String blockId = Convert.toUnsignedLong(block.getId());
            logger.debug("fetchPeerBlockId: height={} → {} (via parseBlock fallback)", height, blockId);
            return blockId;
        } catch (SignumException.ValidationException e) {
            logger.warn("fetchPeerBlockId: height={} parseBlock failed", height, e);
            return null;
        }
    }

    @Override
    public JsonObject getBlacklist() {
        JsonArray blacklisted = new JsonArray();
        Collection<Peer> peers = Peers.getAllPeers();
        for (Peer peer : peers) {
            if (peer.isBlacklisted()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("address", peer.getAnnouncedAddress() != null ? peer.getAnnouncedAddress() : peer.getPeerAddress());
                entry.addProperty("reason", peer.getBlacklistReason());
                entry.addProperty("connectionFailures", peer.getConnectionFailures());
                blacklisted.add(entry);
            }
        }
        JsonObject result = new JsonObject();
        result.add("blacklisted", blacklisted);
        result.addProperty("count", blacklisted.size());
        return result;
    }

    @Override
    public BlockPeerCheck checkBlockPeers(long blockId) {
        Block block = blockchain.getBlock(blockId);
        if (block == null) {
            return BlockPeerCheck.without(Outcome.UNKNOWN_BLOCK, blockId, 0);
        }
        int height = block.getHeight();
        if (height < blockchain.getHeight() - RECENT_BLOCKS) {
            return BlockPeerCheck.without(Outcome.TOO_OLD, blockId, height);
        }
        CompletableFuture<BlockPeerCheck> round;
        synchronized (blockCheckLock) {
            long now = clock.getAsLong();
            blockChecks.values().removeIf(check -> now - check.checkedAt() >= BLOCK_CHECK_CACHE_TTL_MS);
            BlockPeerCheck cached = blockChecks.get(blockId);
            if (cached != null) {
                return cached;
            }
            if (runningBlockCheck != null && !runningBlockCheck.isDone()) {
                // one round at a time: a request for the same block waits for it, any other is turned away
                if (runningBlockId != blockId) {
                    return BlockPeerCheck.without(Outcome.BUSY, blockId, height);
                }
                round = runningBlockCheck;
            } else if (lastBlockCheckStartedAt != Long.MIN_VALUE && now - lastBlockCheckStartedAt < MIN_BLOCK_CHECK_GAP_MS) {
                return BlockPeerCheck.without(Outcome.BUSY, blockId, height);
            } else {
                lastBlockCheckStartedAt = now;
                runningBlockId = blockId;
                runningBlockCheck = round = startBlockCheck(blockId, height, now);
            }
        }
        return round.join();
    }

    /** Asks every connected peer in parallel, each bounded by the probe deadline. */
    private CompletableFuture<BlockPeerCheck> startBlockCheck(long blockId, int height, long startedAt) {
        List<CompletableFuture<BlockPeerState>> answers = new ArrayList<>();
        for (Peer peer : connectedPeers.get()) {
            int peerHeight = peer.getLastKnownHeight();
            if (peer.isBlacklisted() || peerHeight < 0) continue;
            answers.add(CompletableFuture
                    .supplyAsync(() -> askForBlock(peer, peerHeight, blockId, height), probePool)
                    .completeOnTimeout(BlockPeerState.NO_ANSWER, probeTimeoutMs, TimeUnit.MILLISECONDS));
        }
        return CompletableFuture.allOf(answers.toArray(new CompletableFuture[0])).thenApply(done -> {
            Map<BlockPeerState, Integer> counts = new EnumMap<>(BlockPeerState.class);
            answers.forEach(answer -> counts.merge(answer.join(), 1, Integer::sum));
            BlockPeerCheck check = new BlockPeerCheck(Outcome.OK, blockId, height,
                    counts.getOrDefault(BlockPeerState.HAS_IT, 0), counts.getOrDefault(BlockPeerState.MISSING, 0),
                    counts.getOrDefault(BlockPeerState.NOT_YET, 0), counts.getOrDefault(BlockPeerState.NO_ANSWER, 0),
                    startedAt);
            blockChecks.put(blockId, check);
            return check;
        });
    }

    /** Asks one peer; any failure counts as no answer from this peer only. */
    private BlockPeerState askForBlock(Peer peer, int peerHeight, long blockId, int blockHeight) {
        try {
            ProbeResult result = probe.probe(peer, blockId);
            return BlockPeerClassifier.classify(result, heightInOurChain(result.theirTipId()), peerHeight, blockHeight);
        } catch (RuntimeException e) {
            logger.debug("Block check failed for peer {}", peer.getPeerAddress(), e);
            return BlockPeerState.NO_ANSWER;
        }
    }

    @Override
    public void recordRollback(RollbackEvent event) {
        if (event == null) return;
        scheduler.execute(() -> storeRollback(event));
    }

    /** Writes one fork history row; a failure is logged, never thrown into the caller. */
    void storeRollback(RollbackEvent event) {
        try {
            String newTop = event.newTopBlockId() == null ? null : Convert.toUnsignedLong(event.newTopBlockId());
            Db.useDSLContext(ctx -> ctx.insertInto(FORK_HISTORY)
                    .columns(DETECTED_AT, ROLLBACK_HEIGHT, ROLLBACK_DEPTH, OLD_TOP_BLOCK_ID, NEW_TOP_BLOCK_ID, PEER_SOURCE)
                    .values(System.currentTimeMillis(), event.rollbackHeight(), event.depth(),
                            Convert.toUnsignedLong(event.oldTopBlockId()), newTop, event.peerAddress())
                    .execute());
        } catch (RuntimeException e) {
            logger.error("Error recording rollback {}", event, e);
        }
    }

    private void refreshNetworkStatus() {
        // one round at a time, so an older round cannot overwrite a newer snapshot
        synchronized (refreshLock) {
            try {
                Block lastBlock = blockchain.getLastBlock();
                if (lastBlock == null) return;
                cachedStatus.set(buildSnapshot(lastBlock, checkPeers(lastBlock.getId())));
            } catch (Exception e) {
                logger.error("Error refreshing network status", e);
            }
        }
    }

    /** One checked peer: the height it reported, its answer and the resulting status. */
    private record PeerCheck(Peer peer, int height, ProbeResult result, PeerChainStatus status) {
    }

    /**
     * Asks every connected, non-blacklisted peer that reported a height whether it has our tip. The
     * checks run in parallel on {@link #probePool}; a check that misses its deadline counts as
     * unknown, so a peer that drips its answer cannot freeze the snapshot.
     */
    private List<PeerCheck> checkPeers(long ourTipId) {
        List<CompletableFuture<PeerCheck>> checks = new ArrayList<>();
        for (Peer peer : connectedPeers.get()) {
            int peerHeight = peer.getLastKnownHeight();
            if (peer.isBlacklisted() || peerHeight < 0) continue;
            checks.add(CompletableFuture
                    .supplyAsync(() -> check(peer, peerHeight, ourTipId), probePool)
                    .completeOnTimeout(new PeerCheck(peer, peerHeight, ProbeResult.noAnswer(), PeerChainStatus.UNKNOWN),
                            probeTimeoutMs, TimeUnit.MILLISECONDS));
        }
        return checks.stream().map(CompletableFuture::join).toList();
    }

    /** Checks one peer; any failure makes only this peer unknown. */
    private PeerCheck check(Peer peer, int peerHeight, long ourTipId) {
        try {
            ProbeResult result = probe.probe(peer, ourTipId);
            return new PeerCheck(peer, peerHeight, result, PeerChainClassifier.classify(result, weHave(result.theirTipId())));
        } catch (RuntimeException e) {
            logger.debug("Block check failed for peer {}", peer.getPeerAddress(), e);
            return new PeerCheck(peer, peerHeight, ProbeResult.noAnswer(), PeerChainStatus.UNKNOWN);
        }
    }

    /** Height of the block if it is in our chain, else empty. */
    private OptionalInt heightInOurChain(String blockId) {
        if (blockId == null) return OptionalInt.empty();
        Block block = blockchain.getBlock(Convert.parseUnsignedLong(blockId));
        return block == null ? OptionalInt.empty() : OptionalInt.of(block.getHeight());
    }

    /** @throws RuntimeException if the ID is malformed or the lookup fails */
    private boolean weHave(String blockId) {
        return blockId != null && blockchain.hasBlock(Convert.parseUnsignedLong(blockId));
    }

    private JsonObject buildSnapshot(Block lastBlock, List<PeerCheck> checks) {
        Map<PeerChainStatus, Integer> counts = new EnumMap<>(PeerChainStatus.class);
        JsonArray peerArray = new JsonArray();
        for (PeerCheck check : checks) {
            counts.merge(check.status(), 1, Integer::sum);
            peerArray.add(peerEntry(check));
        }
        int answered = checks.size() - counts.getOrDefault(PeerChainStatus.UNKNOWN, 0);
        int agreeing = counts.getOrDefault(PeerChainStatus.ON_CHAIN, 0)
                + counts.getOrDefault(PeerChainStatus.STALE, 0)
                + counts.getOrDefault(PeerChainStatus.AHEAD, 0);
        double consensusPct = answered > 0 ? agreeing * 100.0 / answered : 0.0;

        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("myBlockId", lastBlock.getStringId());
        snapshot.addProperty("myCumulativeDifficulty", lastBlock.getCumulativeDifficulty().toString());
        snapshot.addProperty("myHeight", lastBlock.getHeight());
        snapshot.addProperty("consensusPercent", Math.round(consensusPct * 10.0) / 10.0);
        snapshot.addProperty("totalPeers", checks.size());
        snapshot.addProperty("onChainPeers", counts.getOrDefault(PeerChainStatus.ON_CHAIN, 0));
        snapshot.addProperty("stalePeers", counts.getOrDefault(PeerChainStatus.STALE, 0));
        snapshot.addProperty("aheadPeers", counts.getOrDefault(PeerChainStatus.AHEAD, 0));
        snapshot.addProperty("forkingPeers", counts.getOrDefault(PeerChainStatus.FORKING, 0));
        snapshot.addProperty("unknownPeers", counts.getOrDefault(PeerChainStatus.UNKNOWN, 0));
        snapshot.addProperty("cachedAt", System.currentTimeMillis());
        snapshot.add("peers", peerArray);
        return snapshot;
    }

    private static JsonObject peerEntry(PeerCheck check) {
        Peer peer = check.peer();
        JsonObject entry = new JsonObject();
        entry.addProperty("address", peer.getAnnouncedAddress() != null ? peer.getAnnouncedAddress() : peer.getPeerAddress());
        entry.addProperty("cumulativeDifficulty", peer.getLastKnownCumulativeDifficulty());
        entry.addProperty("height", check.height());
        entry.addProperty("status", check.status().apiValue());
        entry.addProperty("onOurChain", check.status().isOnOurChain());
        // Long: null (no answer) becomes JSON null
        entry.addProperty("latencyMs", check.result().latencyMs());
        entry.addProperty("checkedAt", check.result().checkedAt());
        entry.addProperty("connectionFailures", peer.getConnectionFailures());
        entry.addProperty("blacklisted", false);
        return entry;
    }

    private void pruneOldForkHistory() {
        int ttlDays = propertyService.getInt(Props.WEB_UI_FORK_HISTORY_TTL_DAYS);
        long cutoff = System.currentTimeMillis() - (long) ttlDays * 24 * 60 * 60 * 1000;
        try {
            int deleted = Db.fetchWithDSLContext(ctx -> ctx.deleteFrom(FORK_HISTORY)
                    .where(DETECTED_AT.lt(cutoff))
                    .execute());
            if (deleted > 0) {
                logger.info("Pruned {} old fork_history rows (older than {} days)", deleted, ttlDays);
            }
        } catch (RuntimeException e) {
            logger.error("Error pruning fork_history", e);
        }
    }
}
