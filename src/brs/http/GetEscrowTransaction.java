package brs.http;

import brs.Escrow;
import brs.services.EscrowService;
import brs.util.Convert;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ESCROW_PARAMETER;
import static brs.http.common.ResultFields.ERROR_CODE_RESPONSE;
import static brs.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE;

final class GetEscrowTransaction extends APIServlet.JsonRequestHandler {
	
  private final EscrowService escrowService;
	
  GetEscrowTransaction(EscrowService escrowService) {
    super(new APITag[] {APITag.ACCOUNTS}, ESCROW_PARAMETER);
    this.escrowService = escrowService;
  }
	
  @Override
  JsonElement processRequest(HttpServletRequest req) {
    long escrowId;
    try {
      escrowId = Convert.parseUnsignedLong(Convert.emptyToNull(req.getParameter(ESCROW_PARAMETER)));
    } catch(Exception e) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 3);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid or not specified escrow");
      return response;
    }
		
    Escrow escrow = escrowService.getEscrowTransaction(escrowId);
    if(escrow == null) {
      JsonObject response = new JsonObject();
      response.addProperty(ERROR_CODE_RESPONSE, 5);
      response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Escrow transaction not found");
      return response;
    }
		
    return JSONData.escrowTransaction(escrow);
  }
}
