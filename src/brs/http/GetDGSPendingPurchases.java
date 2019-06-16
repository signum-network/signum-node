package brs.http;

import brs.BurstException;
import brs.DigitalGoodsStore;
import brs.services.DGSGoodsStoreService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.MISSING_SELLER;
import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.PURCHASES_RESPONSE;

public final class GetDGSPendingPurchases extends APIServlet.JsonRequestHandler {

  private final DGSGoodsStoreService dgsGoodStoreService;

  GetDGSPendingPurchases(DGSGoodsStoreService dgsGoodStoreService) {
    super(new APITag[] {APITag.DGS}, SELLER_PARAMETER, FIRST_INDEX_PARAMETER, LAST_INDEX_PARAMETER);
    this.dgsGoodStoreService = dgsGoodStoreService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    long sellerId = ParameterParser.getSellerId(req);

    if (sellerId == 0) {
      return MISSING_SELLER;
    }

    int firstIndex = ParameterParser.getFirstIndex(req);
    int lastIndex = ParameterParser.getLastIndex(req);

    JsonObject response = new JsonObject();
    JsonArray purchasesJSON = new JsonArray();

    for (DigitalGoodsStore.Purchase purchase : dgsGoodStoreService.getPendingSellerPurchases(sellerId, firstIndex, lastIndex)) {
      purchasesJSON.add(JSONData.purchase(purchase));
    }

    response.add(PURCHASES_RESPONSE, purchasesJSON);
    return response;
  }

}
