package brs.http;

import brs.*;
import brs.services.AccountService;
import brs.services.ParameterService;
import brs.services.TimeService;
import brs.util.Convert;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.*;

public final class DGSPurchase extends CreateTransaction {

  private final ParameterService parameterService;
  private final Blockchain blockchain;
  private final AccountService accountService;
  private final TimeService timeService;

  DGSPurchase(ParameterService parameterService, Blockchain blockchain, AccountService accountService, TimeService timeService, APITransactionManager apiTransactionManager) {
    super(new APITag[]{APITag.DGS, APITag.CREATE_TRANSACTION}, apiTransactionManager, GOODS_PARAMETER, PRICE_NQT_PARAMETER, QUANTITY_PARAMETER, DELIVERY_DEADLINE_TIMESTAMP_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
    this.accountService = accountService;
    this.timeService = timeService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    DigitalGoodsStore.Goods goods = parameterService.getGoods(req);
    if (goods.isDelisted()) {
      return UNKNOWN_GOODS;
    }

    int quantity = ParameterParser.getGoodsQuantity(req);
    if (quantity > goods.getQuantity()) {
      return INCORRECT_PURCHASE_QUANTITY;
    }

    long priceNQT = ParameterParser.getPriceNQT(req);
    if (priceNQT != goods.getPriceNQT()) {
      return INCORRECT_PURCHASE_PRICE;
    }

    String deliveryDeadlineString = Convert.emptyToNull(req.getParameter(DELIVERY_DEADLINE_TIMESTAMP_PARAMETER));
    if (deliveryDeadlineString == null) {
      return MISSING_DELIVERY_DEADLINE_TIMESTAMP;
    }
    int deliveryDeadline;
    try {
      deliveryDeadline = Integer.parseInt(deliveryDeadlineString);
      if (deliveryDeadline <= timeService.getEpochTime()) {
        return INCORRECT_DELIVERY_DEADLINE_TIMESTAMP;
      }
    } catch (NumberFormatException e) {
      return INCORRECT_DELIVERY_DEADLINE_TIMESTAMP;
    }

    Account buyerAccount = parameterService.getSenderAccount(req);
    Account sellerAccount = accountService.getAccount(goods.getSellerId());

    Attachment attachment = new Attachment.DigitalGoodsPurchase(goods.getId(), quantity, priceNQT,
        deliveryDeadline, blockchain.getHeight());
    return createTransaction(req, buyerAccount, sellerAccount.getId(), 0, attachment);

  }

}
