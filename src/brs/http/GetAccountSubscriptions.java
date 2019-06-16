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
import java.util.Collection;

import static brs.http.common.Parameters.ACCOUNT_PARAMETER;
import static brs.http.common.Parameters.SUBSCRIPTIONS_RESPONSE;

public final class GetAccountSubscriptions extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;
  private final SubscriptionService subscriptionService;

  GetAccountSubscriptions(ParameterService parameterService, SubscriptionService subscriptionService) {
    super(new APITag[]{APITag.ACCOUNTS}, ACCOUNT_PARAMETER);
    this.parameterService = parameterService;
    this.subscriptionService = subscriptionService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {

    Account account = parameterService.getAccount(req);

    JsonObject response = new JsonObject();

    JsonArray subscriptions = new JsonArray();

    Collection<Subscription> accountSubscriptions = subscriptionService.getSubscriptionsByParticipant(account.getId());

    for (Subscription accountSubscription : accountSubscriptions) {
      subscriptions.add(JSONData.subscription(accountSubscription));
    }

    response.add(SUBSCRIPTIONS_RESPONSE, subscriptions);
    return response;
  }
}
