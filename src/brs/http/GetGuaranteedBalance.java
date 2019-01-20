package brs.http;

import brs.Account;
import brs.BurstException;
import brs.services.ParameterService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ACCOUNT_PARAMETER;
import static brs.http.common.Parameters.NUMBER_OF_CONFIRMATIONS_PARAMETER;
import static brs.http.common.ResultFields.GUARANTEED_BALANCE_NQT_RESPONSE;

;

final class GetGuaranteedBalance extends APIServlet.APIRequestHandler {

  private final ParameterService parameterService;

  GetGuaranteedBalance(ParameterService parameterService) {
    super(new APITag[] {APITag.ACCOUNTS, APITag.FORGING}, ACCOUNT_PARAMETER, NUMBER_OF_CONFIRMATIONS_PARAMETER);
    this.parameterService = parameterService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    Account account = parameterService.getAccount(req);
    int numberOfConfirmations = parameterService.getNumberOfConfirmations(req);

    JsonObject response = new JsonObject();
    if (account == null) {
      response.addProperty(GUARANTEED_BALANCE_NQT_RESPONSE, "0");
    }
    else {
      response.addProperty(GUARANTEED_BALANCE_NQT_RESPONSE, String.valueOf(account.getBalanceNQT()));
    }

    return response;
  }

}
