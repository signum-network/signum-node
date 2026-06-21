package brs.web.api.http.handler;

import static brs.web.api.http.common.JSONResponses.NOT_ENOUGH_ASSETS;
import static brs.web.api.http.common.Parameters.AMOUNT_NQT_PARAMETER;
import static brs.web.api.http.common.Parameters.ASSET_PARAMETER;
import static brs.web.api.http.common.Parameters.ASSET_TO_DISTRIBUTE_PARAMETER;
import static brs.web.api.http.common.Parameters.QUANTITY_MININUM_QNT_PARAMETER;
import static brs.web.api.http.common.Parameters.QUANTITY_QNT_PARAMETER;

import jakarta.servlet.http.HttpServletRequest;

import brs.web.api.http.common.APITransactionManager;
import brs.web.api.http.common.JSONResponses;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonElement;

import brs.Account;
import brs.Account.AccountAsset;
import brs.Asset;
import brs.Attachment;
import brs.Blockchain;
import brs.Signum;
import brs.SignumException;
import brs.Constants;
import brs.assetexchange.AssetExchange;
import brs.fluxcapacitor.FluxValues;
import brs.services.AccountService;
import brs.services.ParameterService;
import brs.util.CollectionWithIndex;
import brs.util.Convert;

public final class DistributeToAssetHolders extends CreateTransaction {

  private final ParameterService parameterService;
  private final Blockchain blockchain;
  private final AssetExchange assetExchange;
  private final AccountService accountService;

  public DistributeToAssetHolders(ParameterService parameterService, Blockchain blockchain,
                                  APITransactionManager apiTransactionManager, AssetExchange assetExchange, AccountService accountService) {
    super(new LegacyDocTag[] {LegacyDocTag.AE, LegacyDocTag.CREATE_TRANSACTION}, apiTransactionManager, ASSET_PARAMETER,
        QUANTITY_MININUM_QNT_PARAMETER, AMOUNT_NQT_PARAMETER, ASSET_TO_DISTRIBUTE_PARAMETER, QUANTITY_QNT_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
    this.assetExchange = assetExchange;
    this.accountService = accountService;
  }

  @Override
  protected
  JsonElement processRequest(HttpServletRequest req) throws SignumException {

    Account account = parameterService.getSenderAccount(req);
    Asset asset = parameterService.getAsset(req);

    long amountNQT = 0L;
    String amountValueNQT = Convert.emptyToNull(req.getParameter(AMOUNT_NQT_PARAMETER));
    if (amountValueNQT != null) {
      try {
        amountNQT = Long.parseLong(amountValueNQT);
      } catch (RuntimeException e) {
        return JSONResponses.incorrect(AMOUNT_NQT_PARAMETER);
      }
      if (amountNQT < 0 || amountNQT >= Constants.MAX_BALANCE_NQT) {
        return JSONResponses.incorrect(AMOUNT_NQT_PARAMETER);
      }
    }

    if(!Signum.getFluxCapacitor().getValue(FluxValues.SMART_TOKEN)) {
      return JSONResponses.incorrect("asset distribution is not enabled yet");
    }

    // another token can also be sent
    long quantityQNT = Convert.parseUnsignedLong(req.getParameter(QUANTITY_QNT_PARAMETER));
    if(quantityQNT < 0) {
      return JSONResponses.incorrect(QUANTITY_QNT_PARAMETER);
    }
    long minimumQuantity = Convert.parseUnsignedLong(req.getParameter(QUANTITY_MININUM_QNT_PARAMETER));

    long assetToDistributeId = 0L;
    String assetToDistributeValue = Convert.emptyToNull(req.getParameter(ASSET_TO_DISTRIBUTE_PARAMETER));
    if (assetToDistributeValue != null) {
      try {
        assetToDistributeId = Convert.parseUnsignedLong(assetToDistributeValue);

        long assetBalance = accountService.getUnconfirmedAssetBalanceQNT(account, assetToDistributeId);
        if (assetBalance < 0 || quantityQNT > assetBalance) {
          return NOT_ENOUGH_ASSETS;
        }
      } catch (RuntimeException e) {
        return JSONResponses.incorrect(ASSET_TO_DISTRIBUTE_PARAMETER);
      }
    }
    else if (quantityQNT != 0L) {
      return JSONResponses.incorrect(QUANTITY_QNT_PARAMETER);
    }

    if(amountNQT == 0L && quantityQNT == 0L) {
      return JSONResponses.incorrect(AMOUNT_NQT_PARAMETER);
    }

    boolean unconfirmed = !Signum.getFluxCapacitor().getValue(FluxValues.DISTRIBUTION_FIX);
    CollectionWithIndex<AccountAsset> holders = assetExchange.getAssetAccounts(asset, false, minimumQuantity, unconfirmed, -1, -1);
    long circulatingSupply = 0;
    for(AccountAsset holder : holders) {
      try {
        circulatingSupply = Convert.safeAdd(circulatingSupply, holder.getQuantityQnt());
      } catch (ArithmeticException e) {
        return JSONResponses.incorrect(QUANTITY_MININUM_QNT_PARAMETER);
      }
    }
    if(circulatingSupply == 0L) {
      return JSONResponses.incorrect(QUANTITY_MININUM_QNT_PARAMETER);
    }

    Attachment attachment = new Attachment.ColoredCoinsAssetDistributeToHolders(asset.getId(), minimumQuantity,
        assetToDistributeId, quantityQNT, blockchain.getHeight());
    return createTransaction(req, account, null, amountNQT, attachment);
  }

}
