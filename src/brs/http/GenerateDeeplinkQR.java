package brs.http;

import brs.deeplink.DeeplinkGenerator;
import brs.http.APIServlet.HttpRequestHandler;
import brs.util.Convert;
import brs.util.StringUtils;
import com.google.zxing.WriterException;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.*;

public class GenerateDeeplinkQR extends HttpRequestHandler {

  private final Logger logger = LoggerFactory.getLogger(GenerateDeeplinkQR.class);
  public static final GenerateDeeplinkQR instance = new GenerateDeeplinkQR();

  private GenerateDeeplinkQR() {
    super(new APITag[]{APITag.UTILS}, ACTION_PARAMETER, PAYLOAD_PARAMETER);
  }

  @Override
  public void processRequest(HttpServletRequest req, HttpServletResponse resp) {
    try {

      final String action = Convert.emptyToNull(req.getParameter(ACTION_PARAMETER));
      final String payload = Convert.emptyToNull(req.getParameter(PAYLOAD_PARAMETER));

      if (StringUtils.isEmpty(action) && !StringUtils.isEmpty(payload)) {
        addErrorMessage(resp, PAYLOAD_WITHOUT_ACTION);
        return;
      }

      DeeplinkGenerator deeplinkGenerator = new DeeplinkGenerator();
      try {
        final BufferedImage qrImage = deeplinkGenerator.generateDeepLinkQrCode(action, payload);
        resp.setContentType("image/jpeg");
        ImageIO.write(qrImage, "jpg", resp.getOutputStream());
        resp.getOutputStream().close();
      } catch (IllegalArgumentException e) {
        logger.error("Problem with arguments", e);
        addErrorMessage(resp, incorrect("arguments", e.getMessage()));
      }
    } catch (WriterException | IOException e) {
      logger.error("Could not generate Deeplink QR code", e);
      resp.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
    }
  }
}
