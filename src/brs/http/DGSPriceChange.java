package brs.http;

import brs.*;
import brs.services.ParameterService;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.UNKNOWN_GOODS;
import static brs.http.common.Parameters.GOODS_PARAMETER;
import static brs.http.common.Parameters.PRICE_NQT_PARAMETER;

public final class DGSPriceChange extends CreateTransaction {

  private final ParameterService parameterService;
  private final Blockchain blockchain;

  DGSPriceChange(ParameterService parameterService, Blockchain blockchain, APITransactionManager apiTransactionManager) {
    super(new APITag[]{APITag.DGS, APITag.CREATE_TRANSACTION}, apiTransactionManager, GOODS_PARAMETER, PRICE_NQT_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    Account account = parameterService.getSenderAccount(req);
    DigitalGoodsStore.Goods goods = parameterService.getGoods(req);
    long priceNQT = ParameterParser.getPriceNQT(req);
    if (goods.isDelisted() || goods.getSellerId() != account.getId()) {
      return UNKNOWN_GOODS;
    }
    Attachment attachment = new Attachment.DigitalGoodsPriceChange(goods.getId(), priceNQT, blockchain.getHeight());
    return createTransaction(req, account, attachment);
  }

}
