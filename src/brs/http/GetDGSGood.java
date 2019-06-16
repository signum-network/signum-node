package brs.http;

import brs.BurstException;
import brs.services.ParameterService;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.GOODS_PARAMETER;

public final class GetDGSGood extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;

  GetDGSGood(ParameterService parameterService) {
    super(new APITag[] {APITag.DGS}, GOODS_PARAMETER);
    this.parameterService = parameterService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    return JSONData.goods(parameterService.getGoods(req));
  }

}
