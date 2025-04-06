package brs.web.api.http.handler;

import brs.Constants;
import brs.SignumException;
import brs.crypto.Crypto;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.ParameterService;
import brs.util.Convert;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static brs.web.api.http.common.JSONResponses.*;
import static brs.web.api.http.common.Parameters.API_KEY_PARAMETER;
import static brs.web.api.http.common.Parameters.SECRET_PHRASE_PARAMETER;
import static brs.web.api.http.common.ResultFields.*;

public final class GetAccountKeys extends ApiServlet.JsonRequestHandler {

  private final ParameterService parameterService;

  private final List<String> apiAdminKeyList;

  public GetAccountKeys(PropertyService propertyService, ParameterService parameterService) {
    super(new LegacyDocTag[]{LegacyDocTag.ADMIN}, API_KEY_PARAMETER, SECRET_PHRASE_PARAMETER);
    this.parameterService = parameterService;
    apiAdminKeyList = propertyService.getStringList(Props.API_ADMIN_KEY_LIST);
  }

  @Override
  protected
  JsonElement processRequest(HttpServletRequest req) throws SignumException {
    String apiKey = req.getParameter(API_KEY_PARAMETER);
    if(!apiAdminKeyList.contains(apiKey)) {
      return ERROR_NOT_ALLOWED;
    }

    String secretPhrase =this.parameterService.getSecretPhrase(req);
    if(secretPhrase.length() < Constants.MIN_SECRET_PHRASE_LENGTH) {
      return WEAK_SECRET_PHRASE;
    }
    JsonObject response = new JsonObject();
    response.addProperty(PUBLIC_KEY_RESPONSE, Convert.toHexString(Crypto.getPublicKey(secretPhrase)));
    response.addProperty(PRIVATE_KEY_RESPONSE, Convert.toHexString(Crypto.getPrivateKey(secretPhrase)));
    return response;
  }

}
