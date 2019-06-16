package brs.http;

import brs.Asset;
import brs.assetexchange.AssetExchange;
import brs.util.Convert;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.FIRST_INDEX_PARAMETER;
import static brs.http.common.Parameters.LAST_INDEX_PARAMETER;
import static brs.http.common.ResultFields.ASSET_IDS_RESPONSE;

public final class GetAssetIds extends APIServlet.JsonRequestHandler {

  private final AssetExchange assetExchange;

  public GetAssetIds(AssetExchange assetExchange) {
    super(new APITag[] {APITag.AE}, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER);
    this.assetExchange = assetExchange;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {

    int firstIndex = ParameterParser.getFirstIndex(req);
    int lastIndex = ParameterParser.getLastIndex(req);

    JsonArray assetIds = new JsonArray();
    for (Asset asset : assetExchange.getAllAssets(firstIndex, lastIndex)) {
      assetIds.add(Convert.toUnsignedLong(asset.getId()));
    }
    JsonObject response = new JsonObject();
    response.add(ASSET_IDS_RESPONSE, assetIds);
    return response;
  }

}
