package brs.http;

import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.math.BigInteger;

import static brs.http.common.Parameters.ID_PARAMETER;

final class LongConvert extends APIServlet.JsonRequestHandler {

  static final LongConvert instance = new LongConvert();

  private LongConvert() {
    super(new APITag[] {APITag.UTILS}, ID_PARAMETER);
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {
    String id = Convert.emptyToNull(req.getParameter(ID_PARAMETER));
    if (id == null) {
      return JSON.emptyJSON;
    }
    JsonObject response = new JsonObject();
    BigInteger bigInteger = new BigInteger(id);
    if (bigInteger.signum() < 0) {
      if (bigInteger.negate().compareTo(Convert.two64) > 0) {
        response.addProperty("error", "overflow");
      }
      else {
        response.addProperty("stringId", bigInteger.add(Convert.two64).toString());
        response.addProperty("longId",   String.valueOf(bigInteger.longValue()));
      }
    }
    else {
      if (bigInteger.compareTo(Convert.two64) >= 0) {
        response.addProperty("error", "overflow");
      }
      else {
        response.addProperty("stringId", bigInteger.toString());
        response.addProperty("longId",   String.valueOf(bigInteger.longValue()));
      }
    }
    return response;
  }

}
