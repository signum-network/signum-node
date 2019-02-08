package brs.http;

import brs.BurstException;
import brs.Order;
import brs.assetexchange.AssetExchange;
import brs.db.BurstIterator;
import brs.services.ParameterService;
import brs.util.Convert;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.*;

final class GetBidOrderIds extends APIServlet.APIRequestHandler {

  private final ParameterService parameterService;
  private final AssetExchange assetExchange;

  GetBidOrderIds(ParameterService parameterService, AssetExchange assetExchange) {
    super(new APITag[]{APITag.AE}, ASSET_PARAMETER, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER);
    this.parameterService = parameterService;
    this.assetExchange = assetExchange;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    long assetId = parameterService.getAsset(req).getId();
    int firstIndex = ParameterParser.getFirstIndex(req);
    int lastIndex = ParameterParser.getLastIndex(req);

    JsonArray orderIds = new JsonArray();
    try (BurstIterator<Order.Bid> bidOrders = assetExchange.getSortedBidOrders(assetId, firstIndex, lastIndex)) {
      while (bidOrders.hasNext()) {
        orderIds.add(Convert.toUnsignedLong(bidOrders.next().getId()));
      }
    }
    JsonObject response = new JsonObject();
    response.add("bidOrderIds", orderIds);
    return response;
  }

}
