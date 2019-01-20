package brs.http;

import brs.*;
import brs.services.ParameterService;
import brs.util.Convert;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.NOT_ENOUGH_FUNDS;
import static brs.http.common.Parameters.*;

final class PlaceBidOrder extends CreateTransaction {

  private final ParameterService parameterService;
  private final Blockchain blockchain;

  PlaceBidOrder(ParameterService parameterService, Blockchain blockchain, APITransactionManager apiTransactionManager) {
    super(new APITag[] {APITag.AE, APITag.CREATE_TRANSACTION}, apiTransactionManager, ASSET_PARAMETER, QUANTITY_QNT_PARAMETER, PRICE_NQT_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    Asset asset = parameterService.getAsset(req);
    long priceNQT = ParameterParser.getPriceNQT(req);
    long quantityQNT = ParameterParser.getQuantityQNT(req);
    long feeNQT = ParameterParser.getFeeNQT(req);
    Account account = parameterService.getSenderAccount(req);

    try {
      if (Convert.safeAdd(feeNQT, Convert.safeMultiply(priceNQT, quantityQNT)) > account.getUnconfirmedBalanceNQT()) {
        return NOT_ENOUGH_FUNDS;
      }
    } catch (ArithmeticException e) {
      return NOT_ENOUGH_FUNDS;
    }

    Attachment attachment = new Attachment.ColoredCoinsBidOrderPlacement(asset.getId(), quantityQNT, priceNQT, blockchain.getHeight());
    return createTransaction(req, account, attachment);
  }

}
