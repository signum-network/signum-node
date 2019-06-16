package brs.http;

import brs.Account;
import brs.BurstException;
import brs.Escrow;
import brs.services.EscrowService;
import brs.services.ParameterService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.http.common.Parameters.ACCOUNT_PARAMETER;
import static brs.http.common.Parameters.ESCROWS_RESPONSE;

public final class GetAccountEscrowTransactions extends APIServlet.JsonRequestHandler {

  private final ParameterService parameterService;

  private final EscrowService escrowService;

  GetAccountEscrowTransactions(ParameterService parameterService, EscrowService escrowService) {
    super(new APITag[]{APITag.ACCOUNTS}, ACCOUNT_PARAMETER);
    this.parameterService = parameterService;
    this.escrowService = escrowService;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) throws BurstException {
    final Account account = parameterService.getAccount(req);

    Collection<Escrow> accountEscrows = escrowService.getEscrowTransactionsByParticipant(account.getId());

    JsonObject response = new JsonObject();

    JsonArray escrows = new JsonArray();

    for (Escrow escrow : accountEscrows) {
      escrows.add(JSONData.escrowTransaction(escrow));
    }

    response.add(ESCROWS_RESPONSE, escrows);
    return response;
  }
}
