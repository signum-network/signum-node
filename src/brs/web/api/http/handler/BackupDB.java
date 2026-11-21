package brs.web.api.http.handler;

import static brs.web.api.http.common.Parameters.FILENAME_PARAMETER;
import static brs.web.api.http.common.JSONResponses.ERROR_NOT_ALLOWED;
import static brs.web.api.http.common.Parameters.API_KEY_PARAMETER;
import static brs.web.api.http.common.ResultFields.ERROR_RESPONSE;


import jakarta.servlet.http.HttpServletRequest;

import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.AdminKeys;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import brs.db.sql.Db;
import brs.props.PropertyService;

public final class BackupDB extends ApiServlet.JsonRequestHandler {

  private final AdminKeys adminKeys;

  public BackupDB(PropertyService propertyService) {
    super(new LegacyDocTag[] {LegacyDocTag.ADMIN}, FILENAME_PARAMETER, API_KEY_PARAMETER);

    adminKeys = AdminKeys.from(propertyService);
  }

  @Override
  protected
  JsonElement processRequest(HttpServletRequest req) {

    JsonObject response = new JsonObject();
    String filename = req.getParameter(FILENAME_PARAMETER);
    String apiKey = req.getParameter(API_KEY_PARAMETER);

    if(!adminKeys.isAllowed(apiKey)) {
      return ERROR_NOT_ALLOWED;
    }

    if(filename == null || filename.length() == 0) {
      response.addProperty(ERROR_RESPONSE, "invalid filename");
      return response;
    }

    Db.backup(filename);

    return response;
  }

  final boolean requirePost() {
    return true;
  }

}
