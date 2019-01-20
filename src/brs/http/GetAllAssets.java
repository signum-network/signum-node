package brs.http;

import brs.Asset;
import brs.assetexchange.AssetExchange;
import brs.db.BurstIterator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.FIRST_INDEX_PARAMETER;
import static brs.http.common.Parameters.LAST_INDEX_PARAMETER;
import static brs.http.common.ResultFields.ASSETS_RESPONSE;

;

public final class GetAllAssets extends AbstractAssetsRetrieval {

  private final AssetExchange assetExchange;

  public GetAllAssets(AssetExchange assetExchange) {
    super(new APITag[] {APITag.AE}, assetExchange, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER);
    this.assetExchange = assetExchange;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {
    int firstIndex = ParameterParser.getFirstIndex(req);
    int lastIndex = ParameterParser.getLastIndex(req);

    JsonObject response = new JsonObject();

    try (BurstIterator<Asset> assets = assetExchange.getAllAssets(firstIndex, lastIndex)) {
      response.add(ASSETS_RESPONSE, assetsToJson(assets));
    }

    return response;
  }

}
