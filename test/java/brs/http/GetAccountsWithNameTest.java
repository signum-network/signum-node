package brs.http;

import brs.Account;
import brs.BurstException;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.db.BurstIterator;
import brs.services.AccountService;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ACCOUNTS_RESPONSE;
import static brs.http.common.Parameters.NAME_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetAccountsWithNameTest extends AbstractUnitTest {

    private AccountService accountService;

    private GetAccountsWithName t;

    @Before
    public void setUp() {
        accountService = mock(AccountService.class);

        t = new GetAccountsWithName(accountService);
    }

    @Test
    public void processRequest() throws BurstException {
        final long targetAccountId = 4L;
        final String targetAccountName = "exampleAccountName";

        final HttpServletRequest req = QuickMocker.httpServletRequest(
                new QuickMocker.MockParam(NAME_PARAMETER, targetAccountName)
        );

        final Account targetAccount = mock(Account.class);
        when(targetAccount.getId()).thenReturn(targetAccountId);
        when(targetAccount.getName()).thenReturn(targetAccountName);

        final BurstIterator<Account> mockIterator = mockBurstIterator(targetAccount);

        when(accountService.getAccountsWithName(targetAccountName)).thenReturn(mockIterator);

        final JSONObject resultOverview = (JSONObject) t.processRequest(req);
        assertNotNull(resultOverview);

        final JSONArray resultList = (JSONArray) resultOverview.get(ACCOUNTS_RESPONSE);
        assertNotNull(resultList);
        assertEquals(1, resultList.size());
    }
}
