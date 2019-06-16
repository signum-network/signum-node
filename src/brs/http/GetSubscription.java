package brs.http;

import brs.Subscription;
import brs.services.SubscriptionService;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.SUBSCRIPTION_PARAMETER;
import static brs.http.common.ResultFields.ERROR_CODE_RESPONSE;
import static brs.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE;

final class GetSubscription extends APIServlet.JsonRequestHandler {
	
  private final SubscriptionService subscriptionService;

  GetSubscription(SubscriptionService subscriptionService) {
    super(new APITag[] {APITag.ACCOUNTS}, SUBSCRIPTION_PARAMETER);
    this.subscriptionService = subscriptionService;
  }
	
  @Override
  JsonElement processRequest(HttpServletRequest req) {
    long subscriptionId;
    try {
      subscriptionId = Convert.parseUnsignedLong(Convert.emptyToNull(req.getParameter(SUBSCRIPTION_PARAMETER)));
    }
    catch(Exception e) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 3);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid or not specified subscription");
      return response;
    }
		
    Subscription subscription = subscriptionService.getSubscription(subscriptionId);

    if(subscription == null) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 5);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Subscription not found");
      return response;
    }
		
    return JSONData.subscription(subscription);
  }
}
