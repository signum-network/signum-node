package brs.http;

import brs.Token;
import brs.services.TimeService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.Constants.TOKEN;
import static brs.Constants.WEBSITE;
import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.SECRET_PHRASE_PARAMETER;

final class GenerateToken extends APIServlet.JsonRequestHandler {

  private final TimeService timeService;

  GenerateToken(TimeService timeService) {
    super(new APITag[] {APITag.TOKENS}, WEBSITE, SECRET_PHRASE_PARAMETER);
    this.timeService = timeService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {

    String secretPhrase = req.getParameter(SECRET_PHRASE_PARAMETER);
    String website = req.getParameter(WEBSITE);
    if (secretPhrase == null) {
      return MISSING_SECRET_PHRASE;
    } else if (website == null) {
      return MISSING_WEBSITE;
    }

    try {

      String tokenString = Token.generateToken(secretPhrase, website.trim(), timeService.getEpochTime());

      JsonObject response = new JsonObject();
      response.addProperty(TOKEN, tokenString);

      return response;

    } catch (RuntimeException e) {
      return INCORRECT_WEBSITE;
    }

  }

  @Override
  boolean requirePost() {
    return true;
  }

}
