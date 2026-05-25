package brs.services;

import brs.Block;
import com.google.gson.JsonObject;

import java.util.List;

public interface NetworkAnalysisService {

    JsonObject getNetworkStatus();

    List<JsonObject> getForkHistory(int limit);

    JsonObject findForkPoint(String peerAddress);

    JsonObject getBlacklist();

    void recordFork(Block poppedBlock);

    void recordForkAsync(Block poppedBlock);
}
