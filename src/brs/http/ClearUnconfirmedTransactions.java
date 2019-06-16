package brs.http;

import brs.TransactionProcessor;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.ResultFields.DONE_RESPONSE;
import static brs.http.common.ResultFields.ERROR_RESPONSE;

public final class ClearUnconfirmedTransactions extends APIServlet.JsonRequestHandler {

  private final TransactionProcessor transactionProcessor;

  ClearUnconfirmedTransactions(TransactionProcessor transactionProcessor) {
    super(new APITag[]{APITag.DEBUG});
    this.transactionProcessor = transactionProcessor;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {
    JsonObject response = new JsonObject();
    try {
      transactionProcessor.clearUnconfirmedTransactions();
      response.addProperty(DONE_RESPONSE, true);
    } catch (RuntimeException e) {
      response.addProperty(ERROR_RESPONSE, e.toString());
    }
    return response;
  }

  @Override
  final boolean requirePost() {
    return true;
  }

}
