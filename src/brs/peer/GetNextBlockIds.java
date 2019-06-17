package brs.peer;

import brs.Blockchain;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collection;

final class GetNextBlockIds implements PeerServlet.PeerRequestHandler {

  private final Blockchain blockchain;

  GetNextBlockIds(Blockchain blockchain) {
    this.blockchain = blockchain;
  }


  @Override
  public JsonElement processRequest(JsonObject request, Peer peer) {

    JsonObject response = new JsonObject();

    JsonArray nextBlockIds = new JsonArray();
    long blockId = Convert.parseUnsignedLong(JSON.getAsString(request.get("blockId")));
    Collection<Long> ids = blockchain.getBlockIdsAfter(blockId, 100);

    for (Long id : ids) {
      nextBlockIds.add(Convert.toUnsignedLong(id));
    }

    response.add("nextBlockIds", nextBlockIds);

    return response;
  }

}
