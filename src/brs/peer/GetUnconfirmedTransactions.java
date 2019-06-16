package brs.peer;

import brs.Transaction;
import brs.TransactionProcessor;
import brs.peer.PeerServlet.ExtendedProcessRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

import static brs.http.common.ResultFields.UNCONFIRMED_TRANSACTIONS_RESPONSE;

final class GetUnconfirmedTransactions extends PeerServlet.ExtendedPeerRequestHandler {

  private final TransactionProcessor transactionProcessor;

  GetUnconfirmedTransactions(TransactionProcessor transactionProcessor) {
    this.transactionProcessor = transactionProcessor;
  }

  @Override
  ExtendedProcessRequest extendedProcessRequest(JsonObject request, Peer peer) {
    JsonObject response = new JsonObject();

    final List<Transaction> unconfirmedTransactions = transactionProcessor.getAllUnconfirmedTransactionsFor(peer);

    JsonArray transactionsData = new JsonArray();
    for (Transaction transaction : unconfirmedTransactions) {
      transactionsData.add(transaction.getJsonObject());
    }

    response.add(UNCONFIRMED_TRANSACTIONS_RESPONSE, transactionsData);

    return new ExtendedProcessRequest(response, () -> transactionProcessor.markFingerPrintsOf(peer, unconfirmedTransactions));
  }

}
