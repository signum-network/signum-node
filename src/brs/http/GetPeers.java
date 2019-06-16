package brs.http;

import brs.peer.Peer;
import brs.peer.Peers;
import brs.util.Convert;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ACTIVE_PARAMETER;
import static brs.http.common.Parameters.STATE_PARAMETER;

final class GetPeers extends APIServlet.JsonRequestHandler {

  static final GetPeers instance = new GetPeers();

  private GetPeers() {
    super(new APITag[] {APITag.INFO}, ACTIVE_PARAMETER, STATE_PARAMETER);
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {

    boolean active = "true".equalsIgnoreCase(req.getParameter(ACTIVE_PARAMETER));
    String stateValue = Convert.emptyToNull(req.getParameter(STATE_PARAMETER));

    JsonArray peers = new JsonArray();
    for (Peer peer : active ? Peers.getActivePeers() : stateValue != null ? Peers.getPeers(Peer.State.valueOf(stateValue)) : Peers.getAllPeers()) {
      peers.add(peer.getPeerAddress());
    }

    JsonObject response = new JsonObject();
    response.add("peers", peers);
    return response;
  }

}
