package brs.http;

import brs.Transaction;
import brs.TransactionProcessor;
import brs.util.Convert;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static brs.http.JSONResponses.INCORRECT_ACCOUNT;
import static brs.http.common.Parameters.ACCOUNT_PARAMETER;
import static brs.http.common.ResultFields.UNCONFIRMED_TRANSACTIONS_IDS_RESPONSE;

final class GetUnconfirmedTransactionIds extends APIServlet.APIRequestHandler {

  private final TransactionProcessor transactionProcessor;

  GetUnconfirmedTransactionIds(TransactionProcessor transactionProcessor) {
    super(new APITag[]{APITag.TRANSACTIONS, APITag.ACCOUNTS}, ACCOUNT_PARAMETER);
    this.transactionProcessor = transactionProcessor;
  }

  @Override
  JsonElement processRequest(HttpServletRequest req) {
    final String accountIdString = Convert.emptyToNull(req.getParameter(ACCOUNT_PARAMETER));

    long accountId = 0;

    if (accountIdString != null) {
      try {
        accountId = Convert.parseAccountId(accountIdString);
      } catch (RuntimeException e) {
        return INCORRECT_ACCOUNT;
      }
    }

    final JsonArray transactionIds = new JsonArray();

    final List<Transaction> unconfirmedTransactions = transactionProcessor.getAllUnconfirmedTransactions();

    for (Transaction transaction : unconfirmedTransactions) {
      if (accountId != 0 && !(accountId == transaction.getSenderId() || accountId == transaction.getRecipientId())) {
        continue;
      }
      transactionIds.add(transaction.getStringId());
    }

    JsonObject response = new JsonObject();

    response.add(UNCONFIRMED_TRANSACTIONS_IDS_RESPONSE, transactionIds);

    return response;
  }

}
