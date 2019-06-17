package brs.http;

import brs.*;
import brs.services.ParameterService;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.ERROR_CODE_RESPONSE;
import static brs.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE;

final class SendMoneyMultiSame extends CreateTransaction {

  private static final String[] commonParameters = new String[] {
      SECRET_PHRASE_PARAMETER, PUBLIC_KEY_PARAMETER, FEE_NQT_PARAMETER,
      DEADLINE_PARAMETER, REFERENCED_TRANSACTION_FULL_HASH_PARAMETER, BROADCAST_PARAMETER,
      RECIPIENTS_PARAMETER, AMOUNT_NQT_PARAMETER};

  private final ParameterService parameterService;
  private final Blockchain blockchain;

  SendMoneyMultiSame(ParameterService parameterService, Blockchain blockchain, APITransactionManager apiTransactionManager) {
    super(new APITag[] {APITag.TRANSACTIONS, APITag.CREATE_TRANSACTION}, apiTransactionManager, true, commonParameters);

    this.parameterService = parameterService;
    this.blockchain = blockchain;
  }
	
  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    long amountNQT = ParameterParser.getAmountNQT(req);
    Account sender = parameterService.getSenderAccount(req);
    String recipientString = Convert.emptyToNull(req.getParameter(RECIPIENTS_PARAMETER));


    if(recipientString == null) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 3);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Recipients not specified");
      return response;
    }
		
    String[] recipientsArray = recipientString.split(";", Constants.MAX_MULTI_SAME_OUT_RECIPIENTS);

    if(recipientsArray.length > Constants.MAX_MULTI_SAME_OUT_RECIPIENTS || recipientsArray.length < 2) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 4);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid number of recipients");
      return response;
    }

    Collection<Long> recipients = new ArrayList<>();

    long totalAmountNQT = amountNQT * recipientsArray.length;
    try {
      for(String recipientId : recipientsArray) {
        recipients.add(Convert.parseUnsignedLong(recipientId));
      }
    }
    catch(Exception e) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 4);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid recipients parameter");
      return response;
    }
		
    if(sender.getBalanceNQT() < totalAmountNQT) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 6);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Insufficient funds");
      return response;
    }

    Attachment.PaymentMultiSameOutCreation attachment = new Attachment.PaymentMultiSameOutCreation(recipients, blockchain.getHeight());

    return createTransaction(req, sender, null, totalAmountNQT, attachment);
  }
}
