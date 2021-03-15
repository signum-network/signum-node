package brs.http;

import brs.BurstException;
import brs.services.ParameterService;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.AT_PARAMETER;
import static brs.http.common.Parameters.HEIGHT_PARAMETER;

class GetATDetails extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;

  GetATDetails(ParameterService parameterService) {
    super(new APITag[] {APITag.AT}, AT_PARAMETER, HEIGHT_PARAMETER);
    this.parameterService = parameterService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    return JSONData.at(parameterService.getAT(req));
  }
}
