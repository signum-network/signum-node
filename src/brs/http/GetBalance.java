package brs.http;

import brs.BurstException;
import brs.http.common.Parameters;
import brs.services.ParameterService;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

public final class GetBalance extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;

  public GetBalance(ParameterService parameterService) {
    super(new APITag[]{APITag.ACCOUNTS}, Parameters.ACCOUNT_PARAMETER);
    this.parameterService = parameterService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    return JSONData.accountBalance(parameterService.getAccount(req));
  }

}
