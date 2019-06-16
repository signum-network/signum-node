package brs.http;

import brs.TransactionProcessor;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

final class GetMyPeerInfo extends APIServlet.JsonRequestHandler {

  private final TransactionProcessor transactionProcessor;

  public GetMyPeerInfo(TransactionProcessor transactionProcessor) {
    super(new APITag[]{APITag.PEER_INFO});
    this.transactionProcessor = transactionProcessor;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {

    JsonObject response = new JsonObject();
    response.addProperty("utsInStore", transactionProcessor.getAmountUnconfirmedTransactions());
    return response;
  }

}
