package brs.http;

import brs.Account;
import brs.Appendix;
import brs.Blockchain;
import brs.Transaction;
import brs.crypto.Crypto;
import brs.services.AccountService;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.SECRET_PHRASE_PARAMETER;
import static brs.http.common.Parameters.TRANSACTION_PARAMETER;

final class ReadMessage extends APIServlet.JsonRequestHandler {

  private static final Logger logger = LoggerFactory.getLogger(ReadMessage.class);

  private final Blockchain blockchain;

  private final AccountService accountService;

  ReadMessage(Blockchain blockchain, AccountService accountService) {
    super(new APITag[] {APITag.MESSAGES}, TRANSACTION_PARAMETER, SECRET_PHRASE_PARAMETER);
    this.blockchain = blockchain;
    this.accountService = accountService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {
    String transactionIdString = Convert.emptyToNull(req.getParameter(TRANSACTION_PARAMETER));
    if (transactionIdString == null) {
      return MISSING_TRANSACTION;
    }

    Transaction transaction;
    try {
      transaction = blockchain.getTransaction(Convert.parseUnsignedLong(transactionIdString));
      if (transaction == null) {
        return UNKNOWN_TRANSACTION;
      }
    } catch (RuntimeException e) {
      return INCORRECT_TRANSACTION;
    }

    JsonObject response = new JsonObject();
    Account senderAccount = accountService.getAccount(transaction.getSenderId());
    Appendix.Message message = transaction.getMessage();
    Appendix.EncryptedMessage encryptedMessage = transaction.getEncryptedMessage();
    Appendix.EncryptToSelfMessage encryptToSelfMessage = transaction.getEncryptToSelfMessage();
    if (message == null && encryptedMessage == null && encryptToSelfMessage == null) {
      return NO_MESSAGE;
    }
    if (message != null) {
      response.addProperty("message", message.isText() ? Convert.toString(message.getMessageBytes()) : Convert.toHexString(message.getMessageBytes()));
    }
    String secretPhrase = Convert.emptyToNull(req.getParameter(SECRET_PHRASE_PARAMETER));
    if (secretPhrase != null) {
      if (encryptedMessage != null) {
        long readerAccountId = Account.getId(Crypto.getPublicKey(secretPhrase));
        Account account = senderAccount.getId() == readerAccountId ? accountService.getAccount(transaction.getRecipientId()) : senderAccount;
        if (account != null) {
          try {
            byte[] decrypted = account.decryptFrom(encryptedMessage.getEncryptedData(), secretPhrase);
            response.addProperty("decryptedMessage", encryptedMessage.isText() ? Convert.toString(decrypted) : Convert.toHexString(decrypted));
          } catch (RuntimeException e) {
            logger.debug("Decryption of message to recipient failed: {}", e);
          }
        }
      }
      if (encryptToSelfMessage != null) {
        Account account = accountService.getAccount(Crypto.getPublicKey(secretPhrase));
        if (account != null) {
          try {
            byte[] decrypted = account.decryptFrom(encryptToSelfMessage.getEncryptedData(), secretPhrase);
            response.addProperty("decryptedMessageToSelf", encryptToSelfMessage.isText() ? Convert.toString(decrypted) : Convert.toHexString(decrypted));
          } catch (RuntimeException e) {
            logger.debug("Decryption of message to self failed: {}", e);
          }
        }
      }
    }
    return response;
  }

}
