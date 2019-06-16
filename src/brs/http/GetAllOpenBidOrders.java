package brs.http;

import brs.Order;
import brs.assetexchange.AssetExchange;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.FIRST_INDEX_PARAMETER;
import static brs.http.common.Parameters.LAST_INDEX_PARAMETER;

public final class GetAllOpenBidOrders extends APIServlet.JsonRequestHandler {

  private final AssetExchange assetExchange;

  GetAllOpenBidOrders(AssetExchange assetExchange) {
    super(new APITag[] {APITag.AE}, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER);
    this.assetExchange = assetExchange;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {

    JsonObject response = new JsonObject();
    JsonArray ordersData = new JsonArray();

    int firstIndex = ParameterParser.getFirstIndex(req);
    int lastIndex = ParameterParser.getLastIndex(req);

    for (Order.Bid bidOrder : assetExchange.getAllBidOrders(firstIndex, lastIndex)) {
      ordersData.add(JSONData.bidOrder(bidOrder));
    }

    response.add("openOrders", ordersData);
    return response;
  }

}
