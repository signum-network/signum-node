package brs.http;

import brs.Account;
import brs.BurstException;
import brs.db.BurstIterator;
import brs.services.AccountService;
import brs.util.Convert;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ACCOUNTS_RESPONSE;
import static brs.http.common.Parameters.NAME_PARAMETER;

;
;

public class GetAccountsWithName extends APIServlet.APIRequestHandler {

    private final AccountService accountService;

    GetAccountsWithName(AccountService accountService) {
        super(new APITag[] {APITag.ACCOUNTS}, NAME_PARAMETER);
        this.accountService = accountService;
    }

    @Override
    JsonElement processRequest(HttpServletRequest request) throws BurstException {
        BurstIterator<Account> accounts = accountService.getAccountsWithName(request.getParameter(NAME_PARAMETER));
        JsonArray accountIds = new JsonArray();

        while (accounts.hasNext()) {
            accountIds.add(Convert.toUnsignedLong(accounts.next().id));
        }

        accounts.close();

        JsonObject response = new JsonObject();
        response.add(ACCOUNTS_RESPONSE, accountIds);
        return response;
    }
}
