package brs.http;

import brs.Block;
import brs.Blockchain;
import brs.BurstException;
import brs.EconomicClustering;
import brs.services.TimeService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.TIMESTAMP_PARAMETER;
import static brs.http.common.ResultFields.*;

final class GetECBlock extends APIServlet.JsonRequestHandler {

  private final Blockchain blockchain;
  private final TimeService timeService;
  private final EconomicClustering economicClustering;

  GetECBlock(Blockchain blockchain, TimeService timeService, EconomicClustering economicClustering) {
    super(new APITag[] {APITag.BLOCKS}, TIMESTAMP_PARAMETER);
    this.blockchain = blockchain;
    this.timeService = timeService;
    this.economicClustering = economicClustering;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    int timestamp = ParameterParser.getTimestamp(req);
    if (timestamp == 0) {
      timestamp = timeService.getEpochTime();
    }
    if (timestamp < blockchain.getLastBlock().getTimestamp() - 15) {
      return JSONResponses.INCORRECT_TIMESTAMP;
    }
    Block ecBlock = economicClustering.getECBlock(timestamp);
    JsonObject response = new JsonObject();
    response.addProperty(EC_BLOCK_ID_RESPONSE, ecBlock.getStringId());
    response.addProperty(EC_BLOCK_HEIGHT_RESPONSE, ecBlock.getHeight());
    response.addProperty(TIMESTAMP_RESPONSE, timestamp);
    return response;
  }

}
