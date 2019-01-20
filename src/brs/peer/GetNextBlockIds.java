package brs.peer;

import brs.Blockchain;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

;
;

final class GetNextBlockIds extends PeerServlet.PeerRequestHandler {

  private final Blockchain blockchain;

  GetNextBlockIds(Blockchain blockchain) {
    this.blockchain = blockchain;
  }


  @Override
  JsonElement processRequest(JsonObject request, Peer peer) {

    JsonObject response = new JsonObject();

    JsonArray nextBlockIds = new JsonArray();
    long blockId = Convert.parseUnsignedLong(JSON.toJsonString(request.get("blockId")));
    List<Long> ids = blockchain.getBlockIdsAfter(blockId, 100);

    for (Long id : ids) {
      nextBlockIds.add(Convert.toUnsignedLong(id));
    }

    response.add("nextBlockIds", nextBlockIds);

    return response;
  }

}
