package brs.http;

import brs.Blockchain;
import brs.BlockchainProcessor;
import brs.http.common.Parameters;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.*;

final class Scan extends APIServlet.APIRequestHandler {

  private final BlockchainProcessor blockchainProcessor;
  private final Blockchain blockchain;

  Scan(BlockchainProcessor blockchainProcessor, Blockchain blockchain) {
    super(new APITag[] {APITag.DEBUG}, NUM_BLOCKS_PARAMETER, HEIGHT_PARAMETER, VALIDATE_PARAMETER);
    this.blockchainProcessor = blockchainProcessor;
    this.blockchain = blockchain;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {
    JsonObject response = new JsonObject();
    try {
      if (Parameters.isTrue(req.getParameter(VALIDATE_PARAMETER))) {
        blockchainProcessor.validateAtNextScan();
      }
      int numBlocks = 0;
      try {
        numBlocks = Integer.parseInt(req.getParameter(NUM_BLOCKS_PARAMETER));
      } catch (NumberFormatException e) {}
      int height = -1;
      try {
        height = Integer.parseInt(req.getParameter(HEIGHT_PARAMETER));
      } catch (NumberFormatException ignore) {}
      long start = System.currentTimeMillis();
      if (numBlocks > 0) {
        blockchainProcessor.scan(blockchain.getHeight() - numBlocks + 1);
      }
      else if (height >= 0) {
        blockchainProcessor.scan(height);
      }
      else {
        response.addProperty(ERROR_RESPONSE, "invalid numBlocks or height");
        return response;
      }
      long end = System.currentTimeMillis();
      response.addProperty(DONE_RESPONSE, true);
      response.addProperty(SCAN_TIME_RESPONSE, (end - start)/1000);
    }
    catch (RuntimeException e) {
      response.addProperty(ERROR_RESPONSE, e.toString());
    }
    return response;
  }

  @Override
  final boolean requirePost() {
    return true;
  }

}
