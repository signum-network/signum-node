package brs.services.impl;

import brs.Block;
import brs.Blockchain;
import brs.SignumException;
import brs.db.sql.Db;
import brs.peer.Peer;
import brs.peer.Peers;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.NetworkAnalysisService;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class NetworkAnalysisServiceImpl implements NetworkAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAnalysisServiceImpl.class);

    private static final Table<Record> FORK_HISTORY = DSL.table("fork_history");
    private static final Field<Long> DETECTED_AT = DSL.field("detected_at", Long.class);
    private static final Field<Integer> ROLLBACK_HEIGHT = DSL.field("rollback_height", Integer.class);
    private static final Field<Integer> ROLLBACK_DEPTH = DSL.field("rollback_depth", Integer.class);
    private static final Field<String> OLD_TOP_BLOCK_ID = DSL.field("old_top_block_id", String.class);
    private static final Field<String> NEW_TOP_BLOCK_ID = DSL.field("new_top_block_id", String.class);
    private static final Field<String> PEER_SOURCE = DSL.field("peer_source", String.class);

    private final Blockchain blockchain;
    private final PropertyService propertyService;

    private final AtomicReference<JsonObject> cachedStatus = new AtomicReference<>(null);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "network-analysis-worker"); t.setDaemon(true); return t; });

    public NetworkAnalysisServiceImpl(Blockchain blockchain, PropertyService propertyService) {
        this.blockchain = blockchain;
        this.propertyService = propertyService;

        int intervalSeconds = propertyService.getInt(Props.WEB_UI_NETWORK_STATUS_INTERVAL_SECONDS);
        scheduler.scheduleAtFixedRate(this::refreshNetworkStatus, 30, intervalSeconds, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::pruneOldForkHistory, 1, 24, TimeUnit.HOURS);
    }

    @Override
    public JsonObject getNetworkStatus() {
        JsonObject cached = cachedStatus.get();
        if (cached != null) return cached;
        refreshNetworkStatus();
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
    public void recordForkAsync(Block poppedBlock) {
        if (poppedBlock == null) return;
        scheduler.submit(() -> recordFork(poppedBlock));
    }

    public void recordFork(Block poppedBlock) {
        if (poppedBlock == null) return;
        try {
            Db.useDSLContext(ctx -> ctx.insertInto(FORK_HISTORY)
                    .columns(DETECTED_AT, ROLLBACK_HEIGHT, ROLLBACK_DEPTH, OLD_TOP_BLOCK_ID, NEW_TOP_BLOCK_ID, PEER_SOURCE)
                    .values(System.currentTimeMillis(), poppedBlock.getHeight(), 1, poppedBlock.getStringId(), null, null)
                    .execute());
        } catch (RuntimeException e) {
            logger.error("Error recording fork event", e);
        }
    }

    private void refreshNetworkStatus() {
        try {
            Block lastBlock = blockchain.getLastBlock();
            if (lastBlock == null) return;

            String myBlockId = lastBlock.getStringId();
            String myCumulativeDifficulty = lastBlock.getCumulativeDifficulty().toString();
            int myHeight = lastBlock.getHeight();
            BigInteger myDifficulty = new BigInteger(myCumulativeDifficulty);

            Collection<Peer> peers = Peers.getAllPeers();
            JsonArray peerArray = new JsonArray();
            int onChain = 0, stale = 0, forking = 0;

            for (Peer peer : peers) {
                if (peer.isBlacklisted()) continue;

                String peerCd = peer.getLastKnownCumulativeDifficulty();
                int peerHeight = peer.getLastKnownHeight();
                if (peerCd == null || peerHeight < 0) continue;

                BigInteger peerDifficulty = new BigInteger(peerCd);
                int heightDiff = Math.abs(myHeight - peerHeight);

                String status;
                boolean onOurChain;
                if (peerDifficulty.equals(myDifficulty)) {
                    status = "on-chain";
                    onOurChain = true;
                    onChain++;
                } else if (heightDiff <= 5) {
                    status = "stale";
                    onOurChain = true;
                    stale++;
                } else {
                    status = "forking";
                    onOurChain = false;
                    forking++;
                }

                JsonObject peerEntry = new JsonObject();
                String address = peer.getAnnouncedAddress() != null ? peer.getAnnouncedAddress() : peer.getPeerAddress();
                peerEntry.addProperty("address", address);
                peerEntry.addProperty("cumulativeDifficulty", peerCd);
                peerEntry.addProperty("height", peerHeight);
                peerEntry.addProperty("onOurChain", onOurChain);
                peerEntry.addProperty("status", status);
                peerEntry.addProperty("connectionFailures", peer.getConnectionFailures());
                peerEntry.addProperty("blacklisted", peer.isBlacklisted());
                peerArray.add(peerEntry);
            }

            int total = peerArray.size();
            double consensusPct = total > 0 ? (onChain + stale) * 100.0 / total : 0.0;

            JsonObject snapshot = new JsonObject();
            snapshot.addProperty("myBlockId", myBlockId);
            snapshot.addProperty("myCumulativeDifficulty", myCumulativeDifficulty);
            snapshot.addProperty("myHeight", myHeight);
            snapshot.addProperty("consensusPercent", Math.round(consensusPct * 10.0) / 10.0);
            snapshot.addProperty("totalPeers", total);
            snapshot.addProperty("onChainPeers", onChain);
            snapshot.addProperty("stalePeers", stale);
            snapshot.addProperty("forkingPeers", forking);
            snapshot.addProperty("cachedAt", System.currentTimeMillis());
            snapshot.add("peers", peerArray);

            cachedStatus.set(snapshot);
        } catch (Exception e) {
            logger.error("Error refreshing network status", e);
        }
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
