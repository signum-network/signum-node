package brs.http;

import brs.Account;
import brs.BurstException;
import brs.services.AccountService;
import brs.services.ParameterService;
import brs.util.Convert;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ACCOUNT_PARAMETER;
import static brs.http.common.ResultFields.*;

public final class GetAccount extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;
  private final AccountService accountService;
  private final String deprecationMessage = "For account assets use getAccountAssets. This will be removed in V3.0";

  GetAccount(ParameterService parameterService, AccountService accountService) {
    super(new APITag[] {APITag.ACCOUNTS}, ACCOUNT_PARAMETER);
    this.parameterService = parameterService;
    this.accountService = accountService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    Account account = parameterService.getAccount(req);

    JsonObject response = JSONData.accountBalance(account);
    JSONData.putAccount(response, ACCOUNT_RESPONSE, account.getId());

    if (account.getPublicKey() != null) {
      response.addProperty(PUBLIC_KEY_RESPONSE, Convert.toHexString(account.getPublicKey()));
    }
    if (account.getName() != null) {
      response.addProperty(NAME_RESPONSE, account.getName());
    }
    if (account.getDescription() != null) {
      response.addProperty(DESCRIPTION_RESPONSE, account.getDescription());
    }

    //Assets logic moved to GetAccountAssets. Remove this in V3
    JsonArray assetBalances = new JsonArray();
    JsonArray unconfirmedAssetBalances = new JsonArray();

    //the account is an AT
    boolean accountIsAT = false;
    try{
      accountIsAT = brs.at.AT.getAT(account.getId()) != null;
    }
    catch(Exception ex)
    {
      accountIsAT = false;
    }

    for (Account.AccountAsset accountAsset : accountService.getAssets(account.getId(), 0, -1)) {

      // the asset balance will show zero if the the asset is issued by an AT which is the current account 
      boolean showAssetBalance = true; 
      if(accountIsAT){
        brs.Asset asset = brs.Burst.getStores().getAssetStore().getAssetTable().getBy(brs.schema.Tables.ASSET.ID.eq(accountAsset.getAssetId()));
        if(asset != null && asset.getAccountId() == account.getId()){
          showAssetBalance = false;
        }
      }
      
      JsonObject assetBalance = new JsonObject();
      assetBalance.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(accountAsset.getAssetId()));
      assetBalance.addProperty(BALANCE_QNT_RESPONSE, showAssetBalance ? String.valueOf(accountAsset.getQuantityQNT()) : "0");
      assetBalances.add(assetBalance);
      JsonObject unconfirmedAssetBalance = new JsonObject();
      unconfirmedAssetBalance.addProperty(ASSET_RESPONSE, Convert.toUnsignedLong(accountAsset.getAssetId()));
      unconfirmedAssetBalance.addProperty(UNCONFIRMED_BALANCE_QNT_RESPONSE, showAssetBalance ? String.valueOf(accountAsset.getUnconfirmedQuantityQNT()) : "0");
      unconfirmedAssetBalances.add(unconfirmedAssetBalance);
    }

    if (assetBalances.size() > 0 || unconfirmedAssetBalances.size() > 0) {
      response.addProperty(DEPRECATION_RESPONSE, deprecationMessage);
    }

    if (assetBalances.size() > 0) {
      response.add(ASSET_BALANCES_RESPONSE, assetBalances);
    }
    if (unconfirmedAssetBalances.size() > 0) {
      response.add(UNCONFIRMED_ASSET_BALANCES_RESPONSE, unconfirmedAssetBalances);
    }

    return response;
  }

}
