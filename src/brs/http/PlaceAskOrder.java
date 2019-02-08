package brs.http;

import brs.*;
import brs.services.AccountService;
import brs.services.ParameterService;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.NOT_ENOUGH_ASSETS;
import static brs.http.common.Parameters.*;

final class PlaceAskOrder extends CreateTransaction {

  private final ParameterService parameterService;
  private final Blockchain blockchain;
  private final AccountService accountService;

  PlaceAskOrder(ParameterService parameterService, Blockchain blockchain, APITransactionManager apiTransactionManager, AccountService accountService) {
    super(new APITag[] {APITag.AE, APITag.CREATE_TRANSACTION}, apiTransactionManager, ASSET_PARAMETER, QUANTITY_QNT_PARAMETER, PRICE_NQT_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
    this.accountService = accountService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    final Asset asset = parameterService.getAsset(req);
    final long priceNQT = ParameterParser.getPriceNQT(req);
    final long quantityQNT = ParameterParser.getQuantityQNT(req);
    final Account account = parameterService.getSenderAccount(req);

    long assetBalance = accountService.getUnconfirmedAssetBalanceQNT(account, asset.getId());
    if (assetBalance < 0 || quantityQNT > assetBalance) {
      return NOT_ENOUGH_ASSETS;
    }

    Attachment attachment = new Attachment.ColoredCoinsAskOrderPlacement(asset.getId(), quantityQNT, priceNQT, blockchain.getHeight());
    return createTransaction(req, account, attachment);

  }

}
