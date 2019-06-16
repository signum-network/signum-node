package brs.http;

import brs.peer.Peer;
import brs.peer.Peers;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.MISSING_PEER;
import static brs.http.JSONResponses.UNKNOWN_PEER;
import static brs.http.common.Parameters.PEER_PARAMETER;

final class GetPeer extends APIServlet.JsonRequestHandler {

  static final GetPeer instance = new GetPeer();

  private GetPeer() {
    super(new APITag[] {APITag.INFO}, PEER_PARAMETER);
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {

    String peerAddress = req.getParameter(PEER_PARAMETER);
    if (peerAddress == null) {
      return MISSING_PEER;
    }

    Peer peer = Peers.getPeer(peerAddress);
    if (peer == null) {
      return UNKNOWN_PEER;
    }

    return JSONData.peer(peer);

  }

}
