package brs.http;

import brs.Account;
import brs.BurstException;
import brs.services.AccountService;
import brs.services.ParameterService;
import brs.util.Convert;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ACCOUNTS_RESPONSE;
import static brs.http.common.Parameters.ACCOUNT_PARAMETER;

public final class GetAccountsWithRewardRecipient extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;
  private final AccountService accountService;

  GetAccountsWithRewardRecipient(ParameterService parameterService, AccountService accountService) {
    super(new APITag[] {APITag.ACCOUNTS, APITag.MINING, APITag.INFO}, ACCOUNT_PARAMETER);
    this.parameterService = parameterService;
    this.accountService = accountService;
  }
	
  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    JsonObject response = new JsonObject();
		
    Account targetAccount = parameterService.getAccount(req);

    JsonArray accounts = new JsonArray();

    for (Account.RewardRecipientAssignment assignment : accountService.getAccountsWithRewardRecipient(targetAccount.getId())) {
      accounts.add(Convert.toUnsignedLong(assignment.getAccountId()));
    }

    if(accountService.getRewardRecipientAssignment(targetAccount) == null) {
      accounts.add(Convert.toUnsignedLong(targetAccount.getId()));
    }
		
    response.add(ACCOUNTS_RESPONSE, accounts);
		
    return response;
  }
}
