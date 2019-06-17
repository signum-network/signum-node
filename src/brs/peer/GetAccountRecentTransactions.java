package brs.peer;

import brs.Account;
import brs.Blockchain;
import brs.Transaction;
import brs.services.AccountService;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * @deprecated This call is no longer made by the other peers so will soon be removed.
 */
@Deprecated
public class GetAccountRecentTransactions implements PeerServlet.PeerRequestHandler {

  private final AccountService accountService;
  private final Blockchain blockchain;

  GetAccountRecentTransactions(AccountService accountService, Blockchain blockchain) {
    this.accountService = accountService;
    this.blockchain = blockchain;
  }
	
  @Override
  public JsonElement processRequest(JsonObject request, Peer peer) {
    JsonObject response = new JsonObject();
    Long accountId = Convert.parseAccountId(JSON.getAsString(request.get("account")));
    Account account = accountService.getAccount(accountId);
    JsonArray transactions = new JsonArray();
    if(account != null) {
      for (Transaction transaction : blockchain.getTransactions(account, 0, (byte)-1, (byte)0, 0, 0, 9, false)) {
        transactions.add(brs.http.JSONData.transaction(transaction, blockchain.getHeight()));
      }
    }
    response.add("transactions", transactions);
		
    return response;
  }

}
