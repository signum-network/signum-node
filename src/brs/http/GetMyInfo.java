package brs.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

final class GetMyInfo extends APIServlet.JsonRequestHandler {

  static final GetMyInfo instance = new GetMyInfo();

  private GetMyInfo() {
    super(new APITag[] {APITag.INFO});
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {

    JsonObject response = new JsonObject();
    response.addProperty("host", req.getRemoteHost());
    response.addProperty("address", req.getRemoteAddr());
    return response;
  }

}
