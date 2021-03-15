package brs.http;

import brs.BurstException;
import brs.services.ParameterService;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.AT_PARAMETER;

final class GetAT extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;

  GetAT(ParameterService parameterService) {
    super(new APITag[]{APITag.AT}, AT_PARAMETER);
    this.parameterService = parameterService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    return JSONData.at(parameterService.getAT(req));
  }

}
