package brs.http;

import brs.services.TimeService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.ResultFields.TIME_RESPONSE;

public final class GetTime extends APIServlet.JsonRequestHandler {

  private final TimeService timeService;

  GetTime(TimeService timeService) {
    super(new APITag[]{APITag.INFO});
    this.timeService = timeService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {
    JsonObject response = new JsonObject();
    response.addProperty(TIME_RESPONSE, timeService.getEpochTime());

    return response;
  }

}
