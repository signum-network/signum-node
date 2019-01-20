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
import static brs.http.common.ResultFields.BID_ORDER_IDS_RESPONSE;

;
;

public final class GetAccountCurrentBidOrderIds extends APIServlet.APIRequestHandler {

  private final ParameterService parameterService;
  private final AssetExchange assetExchange;

  GetAccountCurrentBidOrderIds(ParameterService parameterService, AssetExchange assetExchange) {
    super(new APITag[] {APITag.ACCOUNTS, APITag.AE}, ACCOUNT_PARAMETER, ASSET_PARAMETER, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER);
    this.parameterService = parameterService;
    this.assetExchange = assetExchange;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    long accountId = parameterService.getAccount(req).getId();
    long assetId = 0;
    try {
      assetId = Convert.parseUnsignedLong(req.getParameter(ASSET_PARAMETER));
    } catch (RuntimeException e) {
      // ignore
    }
    int firstIndex = ParameterParser.getFirstIndex(req);
    int lastIndex = ParameterParser.getLastIndex(req);

    BurstIterator<Order.Bid> bidOrders;
    if (assetId == 0) {
      bidOrders = assetExchange.getBidOrdersByAccount(accountId, firstIndex, lastIndex);
    } else {
      bidOrders = assetExchange.getBidOrdersByAccountAsset(accountId, assetId, firstIndex, lastIndex);
    }
    JsonArray orderIds = new JsonArray();
    try {
      while (bidOrders.hasNext()) {
        orderIds.add(Convert.toUnsignedLong(bidOrders.next().getId()));
      }
    } finally {
      bidOrders.close();
    }
    JsonObject response = new JsonObject();
    response.add(BID_ORDER_IDS_RESPONSE, orderIds);
    return response;
  }

}
