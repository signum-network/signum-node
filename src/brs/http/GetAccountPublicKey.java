package brs.http;

import brs.Account;
import brs.BurstException;
import brs.services.ParameterService;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ACCOUNT_PARAMETER;
import static brs.http.common.ResultFields.PUBLIC_KEY_RESPONSE;

public final class GetAccountPublicKey extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;

  GetAccountPublicKey(ParameterService parameterService) {
    super(new APITag[] {APITag.ACCOUNTS}, ACCOUNT_PARAMETER);
    this.parameterService = parameterService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    Account account = parameterService.getAccount(req);

    if (account.getPublicKey() != null) {
      JsonObject response = new JsonObject();
      response.addProperty(PUBLIC_KEY_RESPONSE, Convert.toHexString(account.getPublicKey()));
      return response;
    } else {
      return JSON.emptyJSON;
    }
  }

}
