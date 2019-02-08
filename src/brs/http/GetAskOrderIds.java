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
import static brs.http.common.ResultFields.ASK_ORDER_IDS_RESPONSE;

public final class GetAskOrderIds extends APIServlet.APIRequestHandler {

  private final ParameterService parameterService;
  private final AssetExchange assetExchange;

  GetAskOrderIds(ParameterService parameterService, AssetExchange assetExchange) {
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
    try (BurstIterator<Order.Ask> askOrders = assetExchange.getSortedAskOrders(assetId, firstIndex, lastIndex)) {
      while (askOrders.hasNext()) {
        orderIds.add(Convert.toUnsignedLong(askOrders.next().getId()));
      }
    }

    JsonObject response = new JsonObject();
    response.add(ASK_ORDER_IDS_RESPONSE, orderIds);
    return response;

  }

}
