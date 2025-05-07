package brs.web.api.http.handler;

import brs.Account;
import brs.Blockchain;
import brs.SignumException;
import brs.Transaction;
import brs.services.ParameterService;
import brs.util.CollectionWithIndex;

import brs.util.Convert;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import signumj.entity.SignumAddress;

import javax.servlet.http.HttpServletRequest;

import static brs.web.api.http.common.Parameters.*;
import static brs.web.api.http.common.ResultFields.NEXT_INDEX_RESPONSE;
import static brs.web.api.http.common.ResultFields.TRANSACTIONS_RESPONSE;
import static brs.web.api.http.common.JSONResponses.MISSING_ACCOUNT;

public final class GetAccountTransactions extends ApiServlet.JsonRequestHandler {

  private final ParameterService parameterService;
  private final Blockchain blockchain;

  public GetAccountTransactions(ParameterService parameterService, Blockchain blockchain) {
    super(new LegacyDocTag[]{LegacyDocTag.ACCOUNTS},
      ACCOUNT_PARAMETER,
      SENDER_PARAMETER,
      RECIPIENT_PARAMETER,
      TIMESTAMP_PARAMETER,
      TYPE_PARAMETER,
      SUBTYPE_PARAMETER,
      FIRST_INDEX_PARAMETER,
      LAST_INDEX_PARAMETER,
      NUMBER_OF_CONFIRMATIONS_PARAMETER,
      INCLUDE_INDIRECT_PARAMETER,
      BIDIRECTIONAL_PARAMETER
    );
    this.parameterService = parameterService;
    this.blockchain = blockchain;
  }

  private static Long getAccountIdParameter(String idParameter) {
    String accountId = Convert.emptyToNull(idParameter);
    if (accountId == null) {
      return null;
    }
    SignumAddress accountAddress = Convert.parseAddress(accountId);
    if (accountAddress == null) {
      return null;
    }
    return accountAddress.getSignedLongId();
  }

  @Override
  protected JsonElement processRequest(HttpServletRequest req) throws SignumException {
    Account account = parameterService.getAccount(req, false);

    Long senderId = null, recipientId = null;
    if (account == null) {
      recipientId = getAccountIdParameter(req.getParameter(RECIPIENT_PARAMETER));
      senderId = getAccountIdParameter(req.getParameter(SENDER_PARAMETER));
      if (senderId == null && recipientId == null) {
        // account is pre-dominantly required if sender and recipient are not provided
        throw new ParameterException(MISSING_ACCOUNT);
      }
    }

    byte type;
    byte subtype;
    try {
      type = Byte.parseByte(req.getParameter(TYPE_PARAMETER));
    } catch (NumberFormatException e) {
      type = -1;
    }
    try {
      subtype = Byte.parseByte(req.getParameter(SUBTYPE_PARAMETER));
    } catch (NumberFormatException e) {
      subtype = -1;
    }

    int firstIndex = ParameterParser.getFirstIndex(req);
    int lastIndex = ParameterParser.getLastIndex(req);

    if (lastIndex < firstIndex) {
      throw new IllegalArgumentException("lastIndex must be greater or equal to firstIndex");
    }

    int timestamp = ParameterParser.getTimestamp(req);
    int numberOfConfirmations = parameterService.getNumberOfConfirmations(req);
    boolean includeIndirect = parameterService.getIncludeIndirect(req);

    CollectionWithIndex<Transaction> accountTransactions = account != null
      ? blockchain.getTransactions(
      account,
      numberOfConfirmations,
      type,
      subtype,
      timestamp,
      firstIndex,
      lastIndex,
      includeIndirect)
      : blockchain.getTransactions(
      senderId,
      recipientId,
      numberOfConfirmations,
      type,
      subtype,
      timestamp,
      firstIndex,
      lastIndex,
      includeIndirect,
      parameterService.getBidirectional(req));

    JsonArray transactions = new JsonArray();
    for (Transaction transaction : accountTransactions) {
      transactions.add(JSONData.transaction(transaction, blockchain.getHeight()));
    }

    JsonObject response = new JsonObject();
    response.add(TRANSACTIONS_RESPONSE, transactions);

    if(accountTransactions.hasNextIndex()) {
      response.addProperty(NEXT_INDEX_RESPONSE, accountTransactions.nextIndex());
    }

    return response;
  }
}
