package brs.http;

import brs.services.ATService;
import brs.util.Convert;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.ResultFields.AT_IDS_RESPONSE;

final class GetATIds extends APIServlet.JsonRequestHandler {

  private final ATService atService;

  GetATIds(ATService atService) {
    super(new APITag[] {APITag.AT});
    this.atService = atService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {

    JsonArray atIds = new JsonArray();
    for (Long id : atService.getAllATIds()) {
      atIds.add(Convert.toUnsignedLong(id));
    }

    JsonObject response = new JsonObject();
    response.add(AT_IDS_RESPONSE, atIds);
    return response;
  }

}
