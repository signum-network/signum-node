package brs.peer;

import brs.Block;
import brs.Blockchain;
import brs.Constants;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

;
;

final class GetNextBlocks extends PeerServlet.PeerRequestHandler {

  private final Blockchain blockchain;

  GetNextBlocks(Blockchain blockchain) {
    this.blockchain = blockchain;
  }


  @Override
  JsonElement processRequest(JsonObject request, Peer peer) {

    JsonObject response = new JsonObject();

    List<Block> nextBlocks = new ArrayList<>();
    int totalLength = 0;
    long blockId = Convert.parseUnsignedLong(JSON.getAsString(request.get("blockId")));
    List<? extends Block> blocks = blockchain.getBlocksAfter(blockId, 100);

    for (Block block : blocks) {
      int length = Constants.BLOCK_HEADER_LENGTH + block.getPayloadLength();
      if (totalLength + length > 1048576) {
        break;
      }
      nextBlocks.add(block);
      totalLength += length;
    }

    JsonArray nextBlocksArray = new JsonArray();
    for (Block nextBlock : nextBlocks) {
      nextBlocksArray.add(nextBlock.getJsonObject());
    }
    response.add("nextBlocks", nextBlocksArray);

    return response;
  }

}
