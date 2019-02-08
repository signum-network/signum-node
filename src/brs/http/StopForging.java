package brs.http;

import brs.Generator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.MISSING_SECRET_PHRASE;
import static brs.http.common.Parameters.SECRET_PHRASE_PARAMETER;


final class StopForging extends APIServlet.APIRequestHandler {

  static final StopForging instance = new StopForging();

  private StopForging() {
    super(new APITag[] {APITag.FORGING}, SECRET_PHRASE_PARAMETER);
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {

    String secretPhrase = req.getParameter(SECRET_PHRASE_PARAMETER);
    if (secretPhrase == null) {
      return MISSING_SECRET_PHRASE;
    }

    //Generator generator = Generator.stopForging(secretPhrase);
    Generator.GeneratorState generator = null;

    JsonObject response = new JsonObject();
    response.addProperty("foundAndStopped", generator != null);
    return response;

  }

  @Override
  boolean requirePost() {
    return true;
  }

}
