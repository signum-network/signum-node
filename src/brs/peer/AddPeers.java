package brs.peer;

import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

final class AddPeers implements PeerServlet.PeerRequestHandler {

  static final AddPeers instance = new AddPeers();

  private AddPeers() {}

  @Override
  public JsonElement processRequest(JsonObject request, Peer peer) {
    JsonArray peers = JSON.getAsJsonArray(request.get("peers"));
    if (peers != null && Peers.getMorePeers) {
      for (JsonElement announcedAddress : peers) {
        Peers.addPeer(JSON.getAsString(announcedAddress));
      }
    }
    return JSON.emptyJSON;
  }

}
