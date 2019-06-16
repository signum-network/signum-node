package brs.http;

import brs.Account;
import brs.BurstException;
import brs.crypto.EncryptedData;
import brs.services.AccountService;
import brs.services.ParameterService;
import com.google.gson.JsonElement;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.INCORRECT_RECIPIENT;
import static brs.http.common.Parameters.*;

final class EncryptTo extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;
  private final AccountService accountService;

  EncryptTo(ParameterService parameterService, AccountService accountService) {
    super(new APITag[]{APITag.MESSAGES}, RECIPIENT_PARAMETER, MESSAGE_TO_ENCRYPT_PARAMETER, MESSAGE_TO_ENCRYPT_IS_TEXT_PARAMETER, SECRET_PHRASE_PARAMETER);
    this.parameterService = parameterService;
    this.accountService = accountService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    long recipientId = ParameterParser.getRecipientId(req);
    Account recipientAccount = accountService.getAccount(recipientId);
    if (recipientAccount == null || recipientAccount.getPublicKey() == null) {
      return INCORRECT_RECIPIENT;
    }

    EncryptedData encryptedData = parameterService.getEncryptedMessage(req, recipientAccount, recipientAccount.getPublicKey());
    return JSONData.encryptedData(encryptedData);

  }

}
