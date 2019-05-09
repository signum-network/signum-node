package brs.http;

import brs.Account;
import brs.assetexchange.AssetExchange;
import brs.services.ParameterService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.ASSETS_RESPONSE;

public final class GetAssetsByIssuer extends AbstractAssetsRetrieval {

  private final ParameterService parameterService;
  private final AssetExchange assetExchange;

  GetAssetsByIssuer(ParameterService parameterService, AssetExchange assetExchange) {
    super(new APITag[] {APITag.AE, APITag.ACCOUNTS}, assetExchange, ACCOUNT_PARAMETER, ACCOUNT_PARAMETER, ACCOUNT_PARAMETER, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER);
    this.parameterService = parameterService;
    this.assetExchange = assetExchange;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws ParameterException {
    List<Account> accounts = parameterService.getAccounts(req);
    int firstIndex = ParameterParser.getFirstIndex(req);
    int lastIndex = ParameterParser.getLastIndex(req);

    JsonObject response = new JsonObject();
    JsonArray accountsJsonArray = new JsonArray();
    response.add(ASSETS_RESPONSE, accountsJsonArray);
    for (Account account : accounts) {
      accountsJsonArray.add(assetsToJson(assetExchange.getAssetsIssuedBy(account.getId(), firstIndex, lastIndex).iterator()));
    }
    return response;
  }

}
