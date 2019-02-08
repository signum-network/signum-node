package brs.peer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class GetPeers extends PeerServlet.PeerRequestHandler {

  static final GetPeers instance = new GetPeers();

  private GetPeers() {}


  @Override
  JsonElement processRequest(JsonObject request, Peer peer) {

    JsonObject response = new JsonObject();

    JsonArray peers = new JsonArray();
    for (Peer otherPeer : Peers.getAllPeers()) {

      if (! otherPeer.isBlacklisted() && otherPeer.getAnnouncedAddress() != null
          && otherPeer.getState() == Peer.State.CONNECTED && otherPeer.shareAddress()) {

        peers.add(otherPeer.getAnnouncedAddress());

      }

    }
    response.add("peers", peers);

    return response;
  }

}
