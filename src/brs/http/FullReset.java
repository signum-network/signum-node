package brs.http;

import brs.BlockchainProcessor;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.ResultFields.DONE_RESPONSE;
import static brs.http.common.ResultFields.ERROR_RESPONSE;

public final class FullReset extends APIServlet.JsonRequestHandler {

  private final BlockchainProcessor blockchainProcessor;

  FullReset(BlockchainProcessor blockchainProcessor) {
    super(new APITag[]{APITag.DEBUG});
    this.blockchainProcessor = blockchainProcessor;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {
    JsonObject response = new JsonObject();
    try {
      blockchainProcessor.fullReset();
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
