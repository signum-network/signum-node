package brs.http;

import brs.Asset;
import brs.BurstException;
import brs.assetexchange.AssetExchange;
import brs.services.ParameterService;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ASSET_PARAMETER;

public final class GetAsset extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;
  private final AssetExchange assetExchange;

  GetAsset(ParameterService parameterService, AssetExchange assetExchange) {
    super(new APITag[]{APITag.AE}, ASSET_PARAMETER);
    this.parameterService = parameterService;
    this.assetExchange = assetExchange;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    final Asset asset = parameterService.getAsset(req);

    int tradeCount = assetExchange.getTradeCount(asset.getId());
    int transferCount = assetExchange.getTransferCount(asset.getId());
    int accountsCount = assetExchange.getAssetAccountsCount(asset.getId());

    return JSONData.asset(asset, tradeCount, transferCount, accountsCount);
  }

}
