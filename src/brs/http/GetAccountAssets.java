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

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.*;

public final class GetAccountAssets extends APIServlet.JsonRequestHandler {

    private final ParameterService parameterService;
    private final AccountService accountService;

    GetAccountAssets(ParameterService parameterService, AccountService accountService) {
        super(new APITag[] {APITag.ACCOUNTS}, ACCOUNT_PARAMETER, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER);
        this.parameterService = parameterService;
        this.accountService = accountService;
    }

    @Override
    JsonElement processRequest(HttpServletRequest req) throws BurstException {

        Account account = parameterService.getAccount(req);

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JsonObject response = new JsonObject();

        JsonArray assetBalances = new JsonArray();
        JsonArray unconfirmedAssetBalances = new JsonArray();

        //the account is an AT
        boolean accountIsAT = brs.at.AT.getAT(account.getId()) != null;

        for (Account.AccountAsset accountAsset : accountService.getAssets(account.getId(), firstIndex, lastIndex)) {

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

        response.add(ASSET_BALANCES_RESPONSE, assetBalances);
        response.add(UNCONFIRMED_ASSET_BALANCES_RESPONSE, unconfirmedAssetBalances);

        return response;
    }
}
