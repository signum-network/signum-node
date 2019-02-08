package brs.http;

import brs.*;
import brs.services.ParameterService;
import brs.services.SubscriptionService;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.SUBSCRIPTION_PARAMETER;
import static brs.http.common.ResultFields.ERROR_CODE_RESPONSE;
import static brs.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE;

public final class SubscriptionCancel extends CreateTransaction {

  private final ParameterService parameterService;
  private final SubscriptionService subscriptionService;
  private final Blockchain blockchain;

  public SubscriptionCancel(ParameterService parameterService, SubscriptionService subscriptionService, Blockchain blockchain, APITransactionManager apiTransactionManager) {
    super(new APITag[]{APITag.TRANSACTIONS, APITag.CREATE_TRANSACTION}, apiTransactionManager, SUBSCRIPTION_PARAMETER);
    this.parameterService = parameterService;
    this.blockchain = blockchain;
    this.subscriptionService = subscriptionService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    final Account sender = parameterService.getSenderAccount(req);

    String subscriptionString = Convert.emptyToNull(req.getParameter(SUBSCRIPTION_PARAMETER));
    if (subscriptionString == null) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 3);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Subscription Id not specified");
      return response;
    }

    long subscriptionId;
    try {
      subscriptionId = Convert.parseUnsignedLong(subscriptionString);
    } catch (Exception e) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 4);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Failed to parse subscription id");
      return response;
    }

    Subscription subscription = subscriptionService.getSubscription(subscriptionId);
    if (subscription == null) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 5);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Subscription not found");
      return response;
    }

    if (sender.getId() != subscription.getSenderId() &&
        sender.getId() != subscription.getRecipientId()) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 7);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Must be sender or recipient to cancel subscription");
      return response;
    }

    Attachment.AdvancedPaymentSubscriptionCancel attachment = new Attachment.AdvancedPaymentSubscriptionCancel(subscription.getId(), blockchain.getHeight());

    return createTransaction(req, sender, null, 0, attachment);
  }
}
