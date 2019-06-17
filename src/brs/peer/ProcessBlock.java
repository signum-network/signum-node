package brs.peer;

import brs.Blockchain;
import brs.BlockchainProcessor;
import brs.BurstException;
import brs.util.JSON;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class ProcessBlock implements PeerServlet.PeerRequestHandler {

  private final Blockchain blockchain;
  private final BlockchainProcessor blockchainProcessor;

  public ProcessBlock(Blockchain blockchain, BlockchainProcessor blockchainProcessor) {
    this.blockchain = blockchain;
    this.blockchainProcessor = blockchainProcessor;
  }

  private static final JsonElement ACCEPTED;
  static {
    JsonObject response = new JsonObject();
    response.addProperty("accepted", true);
    ACCEPTED = response;
  }

  private static final JsonElement NOT_ACCEPTED;
  static {
    JsonObject response = new JsonObject();
    response.addProperty("accepted", false);
    NOT_ACCEPTED = response;
  }

  @Override
  public JsonElement processRequest(JsonObject request, Peer peer) {

    try {

      if (! blockchain.getLastBlock().getStringId().equals(JSON.getAsString(request.get("previousBlock")))) {
        // do this check first to avoid validation failures of future blocks and transactions
        // when loading blockchain from scratch
        return NOT_ACCEPTED;
      }
      blockchainProcessor.processPeerBlock(request, peer);
      return ACCEPTED;

    } catch (BurstException|RuntimeException e) {
      if (peer != null) {
        peer.blacklist(e, "received invalid data via requestType=processBlock");
      }
      return NOT_ACCEPTED;
    }

  }

}
