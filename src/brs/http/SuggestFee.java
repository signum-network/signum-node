package brs.http;

import brs.feesuggestions.FeeSuggestion;
import brs.feesuggestions.FeeSuggestionCalculator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.ResultFields.*;

public class SuggestFee extends APIServlet.JsonRequestHandler {

  private final FeeSuggestionCalculator feeSuggestionCalculator;

  public SuggestFee(FeeSuggestionCalculator feeSuggestionCalculator) {
    super(new APITag[]{APITag.FEES});
    this.feeSuggestionCalculator = feeSuggestionCalculator;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {
    final FeeSuggestion feeSuggestion = feeSuggestionCalculator.giveFeeSuggestion();

    final JsonObject response = new JsonObject();

    response.addProperty(CHEAP_FEE_RESPONSE, feeSuggestion.getCheapFee());
    response.addProperty(STANDARD_FEE_RESPONSE, feeSuggestion.getStandardFee());
    response.addProperty(PRIORITY_FEE_RESPONSE, feeSuggestion.getPriorityFee());

    return response;
  }

}
