package brs.peer;

import brs.Block;
import brs.Blockchain;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class GetCumulativeDifficulty implements PeerServlet.PeerRequestHandler {

  private final Blockchain blockchain;

  GetCumulativeDifficulty(Blockchain blockchain) {
    this.blockchain = blockchain;
  }


  @Override
  public JsonElement processRequest(JsonObject request, Peer peer) {
    JsonObject response = new JsonObject();

    Block lastBlock = blockchain.getLastBlock();
    response.addProperty("cumulativeDifficulty", lastBlock.getCumulativeDifficulty().toString());
    response.addProperty("blockchainHeight", lastBlock.getHeight());
    return response;
  }

}
