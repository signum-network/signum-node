package brs.http;

import brs.Account;
import brs.Attachment;
import brs.BurstException;
import brs.services.ParameterService;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.RECIPIENT_PARAMETER;

final class SendMessage extends CreateTransaction {

  private final ParameterService parameterService;

  SendMessage(ParameterService parameterService, APITransactionManager apiTransactionManager) {
    super(new APITag[] {APITag.MESSAGES, APITag.CREATE_TRANSACTION}, apiTransactionManager, RECIPIENT_PARAMETER);
    this.parameterService = parameterService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    long recipient = ParameterParser.getRecipientId(req);
    Account account = parameterService.getSenderAccount(req);
    return createTransaction(req, account, recipient, 0, Attachment.ARBITRARY_MESSAGE);
  }

}
