package brs.http;

import brs.deeplink.DeeplinkGenerator;
import brs.util.Convert;
import brs.util.StringUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;

import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.*;

public class GenerateDeeplink extends APIServlet.JsonRequestHandler {

  private final Logger logger = LoggerFactory.getLogger(GenerateDeeplink.class);
  public static final GenerateDeeplink instance = new GenerateDeeplink();


  private GenerateDeeplink() {
    super(new APITag[]{APITag.UTILS}, ACTION_PARAMETER, PAYLOAD_PARAMETER);
  }

  @Override
  public JsonElement processRequest(HttpServletRequest req) {
    try {

      final String action = Convert.emptyToNull(req.getParameter(ACTION_PARAMETER));
      final String payload = Convert.emptyToNull(req.getParameter(PAYLOAD_PARAMETER));

      if (StringUtils.isEmpty(action) && !StringUtils.isEmpty(payload)) {
        return PAYLOAD_WITHOUT_ACTION;
      }

      DeeplinkGenerator deeplinkGenerator = new DeeplinkGenerator();
      String deepLink = deeplinkGenerator.generateDeepLink(action, payload);
      JsonObject response = new JsonObject();
      response.addProperty("link", deepLink);
      return response;

    } catch (IllegalArgumentException e) {
      logger.error("Problem with arguments", e);
      return incorrect("arguments", e.getMessage());
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }
}
