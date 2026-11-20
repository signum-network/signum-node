package brs.services;

import brs.RollbackEvent;
import com.google.gson.JsonObject;

import java.util.List;

public interface NetworkAnalysisService {

    JsonObject getNetworkStatus();

    List<JsonObject> getForkHistory(int limit);

    JsonObject findForkPoint(String peerAddress);

    JsonObject getBlacklist();

    /** Stores one rollback in the fork history; returns at once, the write runs in the background. */
    void recordRollback(RollbackEvent event);
}
