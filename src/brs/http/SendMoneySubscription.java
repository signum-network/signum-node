package brs.http;

import brs.*;
import brs.services.ParameterService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.ERROR_CODE_RESPONSE;
import static brs.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE;

final class SendMoneySubscription extends CreateTransaction {

  private final ParameterService parameterService;
  private final Blockchain blockchain;
	
  public SendMoneySubscription(ParameterService parameterService, Blockchain blockchain, APITransactionManager apiTransactionManager) {
    super(new APITag[] {APITag.TRANSACTIONS, APITag.CREATE_TRANSACTION}, apiTransactionManager, RECIPIENT_PARAMETER, AMOUNT_NQT_PARAMETER, FREQUENCY_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
  }
	
  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    Account sender = parameterService.getSenderAccount(req);
    Long recipient = ParameterParser.getRecipientId(req);
    long amountNQT = ParameterParser.getAmountNQT(req);
		
    int frequency;
    try {
      frequency = Integer.parseInt(req.getParameter(FREQUENCY_PARAMETER));
    }
    catch(Exception e) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 4);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid or missing frequency parameter");
      return response;
    }
		
    if(frequency < Constants.BURST_SUBSCRIPTION_MIN_FREQ ||
       frequency > Constants.BURST_SUBSCRIPTION_MAX_FREQ) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 4);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid frequency amount");
      return response;
    }
		
    Attachment.AdvancedPaymentSubscriptionSubscribe attachment = new Attachment.AdvancedPaymentSubscriptionSubscribe(frequency, blockchain.getHeight());
		
    return createTransaction(req, sender, recipient, amountNQT, attachment);
  }
}
