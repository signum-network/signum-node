package brs.http;

import brs.Account;
import brs.BurstException;
import brs.Subscription;
import brs.services.ParameterService;
import brs.services.SubscriptionService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ACCOUNT_PARAMETER;

final class GetSubscriptionsToAccount extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;
  private final SubscriptionService subscriptionService;

  GetSubscriptionsToAccount(ParameterService parameterService, SubscriptionService subscriptionService) {
    super(new APITag[] {APITag.ACCOUNTS}, ACCOUNT_PARAMETER);
    this.parameterService = parameterService;
    this.subscriptionService = subscriptionService;
  }
	
  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    final Account account = parameterService.getAccount(req);
		
    JsonObject response = new JsonObject();
		
    JsonArray subscriptions = new JsonArray();

    for (Subscription subscription : subscriptionService.getSubscriptionsToId(account.getId())) {
      subscriptions.add(JSONData.subscription(subscription));
    }
		
    response.add("subscriptions", subscriptions);
    return response;
  }
}
