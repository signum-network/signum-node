package brs.http;

import brs.Account;
import brs.BurstException;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.services.AccountService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.http.common.Parameters.ACCOUNTS_RESPONSE;
import static brs.http.common.Parameters.NAME_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

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

        final Collection<Account> mockIterator = mockCollection(targetAccount);

        when(accountService.getAccountsWithName(targetAccountName)).thenReturn(mockIterator);

        final JsonObject resultOverview = (JsonObject) t.processRequest(req);
        assertNotNull(resultOverview);

        final JsonArray resultList = (JsonArray) resultOverview.get(ACCOUNTS_RESPONSE);
        assertNotNull(resultList);
        assertEquals(1, resultList.size());
    }

    @Test
    public void processRequest_noAccountFound() throws BurstException {
        final String targetAccountName = "exampleAccountName";

        final HttpServletRequest req = QuickMocker.httpServletRequest(
                new QuickMocker.MockParam(NAME_PARAMETER, targetAccountName)
        );

        final Collection<Account> mockIterator = mockCollection();

        when(accountService.getAccountsWithName(targetAccountName)).thenReturn(mockIterator);

        final JsonObject resultOverview = (JsonObject) t.processRequest(req);
        assertNotNull(resultOverview);

        final JsonArray resultList = (JsonArray) resultOverview.get(ACCOUNTS_RESPONSE);
        assertNotNull(resultList);
        assertEquals(0, resultList.size());
    }
}
