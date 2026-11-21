package brs.services;

import brs.RollbackEvent;
import com.google.gson.JsonObject;

import java.util.List;

public interface NetworkAnalysisService {

    JsonObject getNetworkStatus();

    List<JsonObject> getForkHistory(int limit);

    JsonObject findForkPoint(String peerAddress);

    JsonObject getBlacklist();

    /**
     * Asks every connected peer whether it has the block. Only for blocks near the tip, one round at
     * a time across all blocks; a result is reused for a short time.
     */
    BlockPeerCheck checkBlockPeers(long blockId);

    /** Stores one rollback in the fork history; returns at once, the write runs in the background. */
    void recordRollback(RollbackEvent event);
}
