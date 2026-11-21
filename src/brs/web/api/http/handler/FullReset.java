package brs.web.api.http.handler;

import brs.BlockchainProcessor;
import brs.props.PropertyService;

import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.AdminKeys;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletRequest;

import static brs.web.api.http.common.JSONResponses.ERROR_NOT_ALLOWED;
import static brs.web.api.http.common.Parameters.API_KEY_PARAMETER;
import static brs.web.api.http.common.ResultFields.DONE_RESPONSE;
import static brs.web.api.http.common.ResultFields.ERROR_RESPONSE;


public final class FullReset extends ApiServlet.JsonRequestHandler {

  private final BlockchainProcessor blockchainProcessor;

  private final AdminKeys adminKeys;

  public FullReset(BlockchainProcessor blockchainProcessor, PropertyService propertyService) {
    super(new LegacyDocTag[]{LegacyDocTag.ADMIN}, API_KEY_PARAMETER);
    this.blockchainProcessor = blockchainProcessor;

    adminKeys = AdminKeys.from(propertyService);
  }

  @Override
  protected
  JsonElement processRequest(HttpServletRequest req) {
    String apiKey = req.getParameter(API_KEY_PARAMETER);
    if(!adminKeys.isAllowed(apiKey)) {
      return ERROR_NOT_ALLOWED;
    }

    JsonObject response = new JsonObject();
    try {
      blockchainProcessor.fullReset();
      response.addProperty(DONE_RESPONSE, true);
    } catch (RuntimeException e) {
      response.addProperty(ERROR_RESPONSE, e.toString());
    }
    return response;
  }

//  @Override
  final boolean requirePost() {
    return true;
  }

}
