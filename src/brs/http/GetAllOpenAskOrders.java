package brs.http;

import brs.Order;
import brs.assetexchange.AssetExchange;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.FIRST_INDEX_PARAMETER;
import static brs.http.common.Parameters.LAST_INDEX_PARAMETER;
import static brs.http.common.ResultFields.OPEN_ORDERS_RESPONSE;

public final class GetAllOpenAskOrders extends APIServlet.JsonRequestHandler {

  private final AssetExchange assetExchange;

  GetAllOpenAskOrders(AssetExchange assetExchange) {
    super(new APITag[] {APITag.AE}, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER);
    this.assetExchange = assetExchange;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {

    JsonObject response = new JsonObject();
    JsonArray ordersData = new JsonArray();

    int firstIndex = ParameterParser.getFirstIndex(req);
    int lastIndex = ParameterParser.getLastIndex(req);

    for (Order.Ask askOrder : assetExchange.getAllAskOrders(firstIndex, lastIndex)) {
      ordersData.add(JSONData.askOrder(askOrder));
    }

    response.add(OPEN_ORDERS_RESPONSE, ordersData);
    return response;
  }

}
