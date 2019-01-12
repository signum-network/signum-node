package brs.http;

import brs.Account;
import brs.BurstException;
import brs.db.BurstIterator;
import brs.services.AccountService;
import brs.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ACCOUNTS_RESPONSE;
import static brs.http.common.Parameters.NAME_PARAMETER;

public class GetAccountsWithName extends APIServlet.APIRequestHandler {

    private final AccountService accountService;

    GetAccountsWithName(AccountService accountService) {
        super(new APITag[] {APITag.ACCOUNTS}, NAME_PARAMETER);
        this.accountService = accountService;
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest request) throws BurstException {
        BurstIterator<Account> accounts = accountService.getAccountsWithName(request.getParameter(NAME_PARAMETER));
        JSONArray accountIds = new JSONArray();

        while (accounts.hasNext()) {
            accountIds.add(Convert.toUnsignedLong(accounts.next().id));
        }

        accounts.close();

        JSONObject response = new JSONObject();
        response.put(ACCOUNTS_RESPONSE, accountIds);
        return response;
    }
}
