package brs.http;

import brs.Account;
import brs.Blockchain;
import brs.BurstException;
import brs.services.AccountService;
import brs.services.ParameterService;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ACCOUNT_PARAMETER;
import static brs.http.common.ResultFields.REWARD_RECIPIENT_RESPONSE;

final class GetRewardRecipient extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;
  private final Blockchain blockchain;
  private final AccountService accountService;

  GetRewardRecipient(ParameterService parameterService, Blockchain blockchain, AccountService accountService) {
    super(new APITag[]{APITag.ACCOUNTS, APITag.MINING, APITag.INFO}, ACCOUNT_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
    this.accountService = accountService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    JsonObject response = new JsonObject();

    final Account account = parameterService.getAccount(req);
    Account.RewardRecipientAssignment assignment = accountService.getRewardRecipientAssignment(account);
    long height = blockchain.getLastBlock().getHeight();
    if (assignment == null) {
      response.addProperty(REWARD_RECIPIENT_RESPONSE, Convert.toUnsignedLong(account.getId()));
    } else if (assignment.getFromHeight() > height + 1) {
      response.addProperty(REWARD_RECIPIENT_RESPONSE, Convert.toUnsignedLong(assignment.getPrevRecipientId()));
    } else {
      response.addProperty(REWARD_RECIPIENT_RESPONSE, Convert.toUnsignedLong(assignment.getRecipientId()));
    }

    return response;
  }

}
