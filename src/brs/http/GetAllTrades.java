package brs.http;

import brs.Asset;
import brs.BurstException;
import brs.Trade;
import brs.assetexchange.AssetExchange;
import brs.http.common.Parameters;
import brs.util.FilteringIterator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.TRADES_RESPONSE;

public final class GetAllTrades extends APIServlet.JsonRequestHandler {

  private final AssetExchange assetExchange;

  GetAllTrades(AssetExchange assetExchange) {
    super(new APITag[] {APITag.AE}, TIMESTAMP_PARAMETER, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER, INCLUDE_ASSET_INFO_PARAMETER);
    this.assetExchange = assetExchange;
  }
    
  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    final int timestamp = ParameterParser.getTimestamp(req);
    final int firstIndex = ParameterParser.getFirstIndex(req);
    final int lastIndex = ParameterParser.getLastIndex(req);
    final boolean includeAssetInfo = !Parameters.isFalse(req.getParameter(INCLUDE_ASSET_INFO_PARAMETER));

    final JsonObject response = new JsonObject();
    final JsonArray trades = new JsonArray();

    FilteringIterator<Trade> tradeIterator = new FilteringIterator<>(
            assetExchange.getAllTrades(0, -1),
            trade -> trade.getTimestamp() >= timestamp, firstIndex, lastIndex);
    while (tradeIterator.hasNext()) {
      final Trade trade = tradeIterator.next();
      final Asset asset = includeAssetInfo ? assetExchange.getAsset(trade.getAssetId()) : null;

      trades.add(JSONData.trade(trade, asset));
    }

    response.add(TRADES_RESPONSE, trades);
    return response;
  }

}
