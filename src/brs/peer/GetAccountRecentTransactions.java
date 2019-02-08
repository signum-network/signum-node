package brs.peer;

import brs.Account;
import brs.Blockchain;
import brs.Transaction;
import brs.db.BurstIterator;
import brs.services.AccountService;
import brs.util.Convert;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class GetAccountRecentTransactions extends PeerServlet.PeerRequestHandler {

  private final AccountService accountService;
  private final Blockchain blockchain;

  GetAccountRecentTransactions(AccountService accountService, Blockchain blockchain) {
    this.accountService = accountService;
    this.blockchain = blockchain;
  }
	
  @Override
  JsonElement processRequest(JsonObject request, Peer peer) {
		
    JsonObject response = new JsonObject();
		
    try {
      Long accountId = Convert.parseAccountId(JSON.getAsString(request.get("account")));
      Account account = accountService.getAccount(accountId);
      JsonArray transactions = new JsonArray();
      if(account != null) {
        BurstIterator<? extends Transaction> iterator = blockchain.getTransactions(account, 0, (byte)-1, (byte)0, 0, 0, 9);
        while(iterator.hasNext()) {
          Transaction transaction = iterator.next();
          transactions.add(brs.http.JSONData.transaction(transaction, blockchain.getHeight()));
        }
      }
      response.add("transactions", transactions);
    }
    catch(Exception e) {
    }
		
    return response;
  }

}
