package brs.http;

import brs.Account;
import brs.BurstException;
import brs.services.ATService;
import brs.services.AccountService;
import brs.services.ParameterService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static brs.http.common.Parameters.ACCOUNT_PARAMETER;
import static brs.http.common.ResultFields.ATS_RESPONSE;

public final class GetAccountATs extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;
  private final ATService atService;
  private final AccountService accountService;

  GetAccountATs(ParameterService parameterService, ATService atService, AccountService accountService) {
    super(new APITag[] {APITag.AT, APITag.ACCOUNTS}, ACCOUNT_PARAMETER);
    this.parameterService = parameterService;
    this.atService = atService;
    this.accountService = accountService;
  }
	
  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    Account account = parameterService.getAccount(req); // TODO this is super redundant
		
    List<Long> atIds = atService.getATsIssuedBy(account.getId());
    JsonArray ats = new JsonArray();
    for(long atId : atIds) {
      ats.add(JSONData.at(atService.getAT(atId), accountService));
    }
		
    JsonObject response = new JsonObject();
    response.add(ATS_RESPONSE, ats);
    return response;
  }
}
