package brs.peer;

import brs.Account;
import brs.services.AccountService;
import brs.util.JSON;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import static brs.common.TestConstants.TEST_ACCOUNT_ID;
import static brs.common.TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED;
import static brs.peer.GetAccountBalance.ACCOUNT_ID_PARAMETER_FIELD;
import static brs.peer.GetAccountBalance.BALANCE_NQT_RESPONSE_FIELD;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Deprecated
public class GetAccountBalanceTest {

  private GetAccountBalance t;

  private AccountService mockAccountService;

  @Before
  public void setUp() {
    mockAccountService = mock(AccountService.class);

    t = new GetAccountBalance(mockAccountService);
  }

  @Test
  public void processRequest() {
    final JsonObject req = new JsonObject();
    req.addProperty(ACCOUNT_ID_PARAMETER_FIELD, TEST_ACCOUNT_ID);
    final Peer peer = mock(Peer.class);

    long mockBalanceNQT = 5;
    Account mockAccount = mock(Account.class);
    when(mockAccount.getBalanceNQT()).thenReturn(mockBalanceNQT);

    when(mockAccountService.getAccount(eq(TEST_ACCOUNT_NUMERIC_ID_PARSED))).thenReturn(mockAccount);

    final JsonObject result = (JsonObject) t.processRequest(req, peer);

    assertEquals("" + mockBalanceNQT, JSON.getAsString(result.get(BALANCE_NQT_RESPONSE_FIELD)));
  }

  @Test
  public void processRequest_notExistingAccount() {
    final JsonObject req = new JsonObject();
    req.addProperty(ACCOUNT_ID_PARAMETER_FIELD, TEST_ACCOUNT_ID);
    final Peer peer = mock(Peer.class);

    when(mockAccountService.getAccount(eq(TEST_ACCOUNT_NUMERIC_ID_PARSED))).thenReturn(null);

    final JsonObject result = (JsonObject) t.processRequest(req, peer);

    assertEquals("0", JSON.getAsString(result.get(BALANCE_NQT_RESPONSE_FIELD)));
  }

}
