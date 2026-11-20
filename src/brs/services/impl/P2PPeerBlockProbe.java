package brs.services.impl;

import brs.peer.Peer;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.concurrent.TimeUnit;

/**
 * Asks over P2P with {@code getMilestoneBlockIds} and {@code lastBlockId}. A peer that has the block
 * answers with exactly that ID, plus {@code last: true} when it is also its newest block; a peer
 * without it answers with milestones of its own chain, starting at its newest block.
 * This is the request the sync loop uses to find the common block with its feeder, so every node
 * understands it.
 */
final class P2PPeerBlockProbe implements PeerBlockProbe {

    @Override
    public ProbeResult probe(Peer peer, long blockId) {
        String blockIdString = Convert.toUnsignedLong(blockId);
        JsonObject request = new JsonObject();
        request.addProperty("requestType", "getMilestoneBlockIds");
        request.addProperty("lastBlockId", blockIdString);

        long start = System.nanoTime();
        JsonObject response = peer.send(JSON.prepareRequest(request));
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        if (response == null || response.has("error")) {
            return ProbeResult.noAnswer();
        }
        JsonElement milestones = response.get("milestoneBlockIds");
        if (milestones == null || !milestones.isJsonArray()) {
            return ProbeResult.noAnswer();
        }
        JsonArray ids = milestones.getAsJsonArray();
        long checkedAt = System.currentTimeMillis();
        String firstId = ids.isEmpty() ? null : JSON.getAsString(ids.get(0));
        if (blockIdString.equals(firstId)) {
            boolean last = response.has("last") && JSON.getAsBoolean(response.get("last"));
            return new ProbeResult(true, last, null, latencyMs, checkedAt);
        }
        // without the block, the peer lists milestones of its own chain, starting at its newest block
        return new ProbeResult(false, false, firstId, latencyMs, checkedAt);
    }
}
