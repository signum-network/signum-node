package brs.http;

import brs.Asset;
import brs.assetexchange.AssetExchange;
import brs.db.BurstIterator;
import com.google.gson.JsonArray;

abstract class AbstractAssetsRetrieval extends APIServlet.APIRequestHandler  {

  private final AssetExchange assetExchange;

  AbstractAssetsRetrieval(APITag[] apiTags, AssetExchange assetExchange, String... parameters) {
    super(apiTags, parameters);
    this.assetExchange = assetExchange;
  }

  JsonArray assetsToJson(BurstIterator<Asset> assets) {
    final JsonArray assetsJsonArray = new JsonArray();

    while (assets.hasNext()) {
      final Asset asset = assets.next();

      int tradeCount = assetExchange.getTradeCount(asset.getId());
      int transferCount = assetExchange.getTransferCount(asset.getId());
      int accountsCount = assetExchange.getAssetAccountsCount(asset.getId());

      assetsJsonArray.add(JSONData.asset(asset, tradeCount, transferCount, accountsCount));
    }

    return assetsJsonArray;
  }
}
