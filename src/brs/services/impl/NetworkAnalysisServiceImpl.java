package brs.services.impl;

import brs.Block;
import brs.Blockchain;
import brs.peer.Peer;
import brs.peer.Peers;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.NetworkAnalysisService;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static brs.db.sql.Db.getConnection;

public class NetworkAnalysisServiceImpl implements NetworkAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(NetworkAnalysisServiceImpl.class);

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
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT detected_at, rollback_height, rollback_depth, old_top_block_id, new_top_block_id, peer_source " +
                     "FROM fork_history ORDER BY detected_at DESC LIMIT ?")) {
            ps.setInt(1, Math.min(limit, 200));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("detectedAt", rs.getLong("detected_at"));
                    entry.addProperty("rollbackHeight", rs.getInt("rollback_height"));
                    entry.addProperty("rollbackDepth", rs.getInt("rollback_depth"));
                    entry.addProperty("oldTopBlockId", rs.getString("old_top_block_id"));
                    entry.addProperty("newTopBlockId", rs.getString("new_top_block_id"));
                    entry.addProperty("peerSource", rs.getString("peer_source"));
                    result.add(entry);
                }
            }
        } catch (SQLException e) {
            logger.error("Error reading fork_history", e);
        }
        return result;
    }

    @Override
    public JsonObject findForkPoint(String peerAddress) {
        Peer peer = Peers.getPeer(peerAddress);
        JsonObject result = new JsonObject();
        result.addProperty("peer", peerAddress);

        if (peer == null) {
            result.addProperty("error", "Peer not found");
            return result;
        }

        JsonObject cdRequest = new JsonObject();
        cdRequest.addProperty("requestType", "getCumulativeDifficulty");
        JsonObject cdResponse = peer.send(cdRequest);
        if (cdResponse == null || cdResponse.has("error")) {
            result.addProperty("error", "Cannot reach peer");
            return result;
        }

        int peerHeight = JSON.getAsInt(cdResponse.get("blockchainHeight"));
        int myHeight = blockchain.getHeight();
        int maxLookback = propertyService.getInt(Props.WEB_UI_FORK_POINT_MAX_LOOKBACK);

        int lo = Math.max(1, Math.min(myHeight, peerHeight) - maxLookback);
        int hi = Math.min(myHeight, peerHeight) - 1;
        int forkHeight = lo;
        int steps = 0;

        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            steps++;

            String ourBlockId = Convert.toUnsignedLong(blockchain.getBlockIdAtHeight(mid));

            // Ask peer for blocks starting at height mid-1 with numBlocks=1
            // nextBlocks[0] is block at height mid; its previousBlock field = block ID at mid-1
            // Instead, we ask from mid-1 to get block at mid in nextBlocks[0]
            // nextBlocks[0].previousBlock is not what we want — we need the block AT mid.
            // Workaround: ask for getBlocksFromHeight with height=mid to get block at mid+1,
            // then read nextBlocks[0].previousBlock = blockId at mid.
            JsonObject req = new JsonObject();
            req.addProperty("requestType", "getBlocksFromHeight");
            req.addProperty("height", mid);
            req.addProperty("numBlocks", 1);
            JsonObject resp = peer.send(req);

            if (resp == null || resp.has("error")) {
                result.addProperty("error", "Peer communication error during search");
                return result;
            }

            JsonArray nextBlocks = JSON.getAsJsonArray(resp.get("nextBlocks"));
            if (nextBlocks == null || nextBlocks.size() == 0) {
                // Peer has no block at mid+1 — search lower
                hi = mid - 1;
                continue;
            }

            String peerPrevBlockId = JSON.getAsString(nextBlocks.get(0).getAsJsonObject().get("previousBlock"));
            String ourBlockIdAtMid = Convert.toUnsignedLong(blockchain.getBlockIdAtHeight(mid));

            if (ourBlockIdAtMid.equals(peerPrevBlockId)) {
                // Agree at height mid → fork is higher
                forkHeight = mid;
                lo = mid + 1;
            } else {
                // Disagree at height mid → fork is lower
                hi = mid - 1;
            }
        }

        Block forkBlock = blockchain.getBlockAtHeight(forkHeight);
        result.addProperty("forkAtHeight", forkHeight);
        result.addProperty("forkAtBlockId", forkBlock != null ? forkBlock.getStringId() : "unknown");
        result.addProperty("ourBlockIdAtFork", Convert.toUnsignedLong(blockchain.getBlockIdAtHeight(forkHeight)));
        result.addProperty("searchSteps", steps);
        return result;
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
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO fork_history (detected_at, rollback_height, rollback_depth, old_top_block_id, new_top_block_id, peer_source) " +
                     "VALUES (?, ?, 1, ?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setInt(2, poppedBlock.getHeight());
            ps.setString(3, poppedBlock.getStringId());
            ps.setString(4, null);
            ps.setString(5, null);
            ps.executeUpdate();
        } catch (SQLException e) {
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
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM fork_history WHERE detected_at < ?")) {
            ps.setLong(1, cutoff);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                logger.info("Pruned {} old fork_history rows (older than {} days)", deleted, ttlDays);
            }
        } catch (SQLException e) {
            logger.error("Error pruning fork_history", e);
        }
    }
}
