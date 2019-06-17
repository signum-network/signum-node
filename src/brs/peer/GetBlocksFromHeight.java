package brs.peer;

import brs.Block;
import brs.Blockchain;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collection;

final class GetBlocksFromHeight implements PeerServlet.PeerRequestHandler {

  private final Blockchain blockchain;

  GetBlocksFromHeight(Blockchain blockchain) {
    this.blockchain = blockchain;
  }


  @Override
  public JsonElement processRequest(JsonObject request, Peer peer) {
    JsonObject response = new JsonObject();
    int blockHeight = JSON.getAsInt(request.get("height"));
    int numBlocks = 100;

    try {
      numBlocks = JSON.getAsInt(request.get("numBlocks"));
    } catch (Exception ignored) {
    }

    //small failsafe
    if(numBlocks < 1 || numBlocks > 1400) {
    	numBlocks = 100;
    }
    if(blockHeight < 0) {
    	blockHeight = 0;
    }
    	    
    long blockId =  blockchain.getBlockIdAtHeight(blockHeight);
    Collection<? extends Block> blocks = blockchain.getBlocksAfter(blockId, numBlocks);
    JsonArray nextBlocksArray = new JsonArray();
    for (Block nextBlock : blocks) {
      nextBlocksArray.add(nextBlock.getJsonObject());
    }
    response.add("nextBlocks", nextBlocksArray);
    return response;
  }

}
