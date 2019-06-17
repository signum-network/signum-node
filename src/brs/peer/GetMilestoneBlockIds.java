package brs.peer;

import brs.Block;
import brs.Blockchain;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GetMilestoneBlockIds implements PeerServlet.PeerRequestHandler {

  private static final Logger logger = LoggerFactory.getLogger(GetMilestoneBlockIds.class);

  private final Blockchain blockchain;

  GetMilestoneBlockIds(Blockchain blockchain) {
    this.blockchain = blockchain;
  }

  @Override
  public JsonElement processRequest(JsonObject request, Peer peer) {

    JsonObject response = new JsonObject();
    try {

      JsonArray milestoneBlockIds = new JsonArray();

      String lastBlockIdString = JSON.getAsString(request.get("lastBlockId"));
      if (lastBlockIdString != null) {
        long lastBlockId = Convert.parseUnsignedLong(lastBlockIdString);
        long myLastBlockId = blockchain.getLastBlock().getId();
        if (myLastBlockId == lastBlockId || blockchain.hasBlock(lastBlockId)) {
          milestoneBlockIds.add(lastBlockIdString);
          response.add("milestoneBlockIds", milestoneBlockIds);
          if (myLastBlockId == lastBlockId) {
            response.addProperty("last", Boolean.TRUE);
          }
          return response;
        }
      }

      long blockId;
      int height;
      int jump;
      int limit = 10;
      int blockchainHeight = blockchain.getHeight();
      String lastMilestoneBlockIdString = JSON.getAsString(request.get("lastMilestoneBlockId"));
      if (lastMilestoneBlockIdString != null) {
        Block lastMilestoneBlock = blockchain.getBlock(Convert.parseUnsignedLong(lastMilestoneBlockIdString));
        if (lastMilestoneBlock == null) {
          throw new IllegalStateException("Don't have block " + lastMilestoneBlockIdString);
        }
        height = lastMilestoneBlock.getHeight();
        jump = Math.min(1440, Math.max(blockchainHeight - height, 1));
        height = Math.max(height - jump, 0);
      } else if (lastBlockIdString != null) {
        height = blockchainHeight;
        jump = 10;
      } else {
        peer.blacklist("GetMilestoneBlockIds");
        response.addProperty("error", "Old getMilestoneBlockIds protocol not supported, please upgrade");
        return response;
      }
      blockId = blockchain.getBlockIdAtHeight(height);

      while (height > 0 && limit-- > 0) {
        milestoneBlockIds.add(Convert.toUnsignedLong(blockId));
        blockId = blockchain.getBlockIdAtHeight(height);
        height = height - jump;
      }
      response.add("milestoneBlockIds", milestoneBlockIds);

    } catch (RuntimeException e) {
      logger.debug(e.toString());
      response.addProperty("error", e.toString());
    }

    return response;
  }

}
