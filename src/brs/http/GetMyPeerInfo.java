package brs.http;

import brs.BlockchainProcessor;
import brs.TransactionProcessor;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

final class GetMyPeerInfo extends APIServlet.APIRequestHandler {

  private final BlockchainProcessor blockchainProcessor;
  private final TransactionProcessor transactionProcessor;

  public GetMyPeerInfo(BlockchainProcessor blockchainProcessor, TransactionProcessor transactionProcessor) {
    super(new APITag[]{APITag.PEER_INFO});
    this.blockchainProcessor = blockchainProcessor;
    this.transactionProcessor = transactionProcessor;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {

    JsonObject response = new JsonObject();
    response.addProperty("walletTTSD", blockchainProcessor.getWalletTTSD());
    response.addProperty("utsInStore", transactionProcessor.getAmountUnconfirmedTransactions());
    return response;
  }

}
