package brs.peer;

import brs.Account;
import brs.Blockchain;
import brs.Transaction;
import brs.TransactionType.DigitalGoods;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.JSONParam;
import brs.common.TestConstants;
import brs.services.AccountService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAccountRecentTransactionsTest extends AbstractUnitTest {

  private GetAccountRecentTransactions t;

  private AccountService mockAccountService;
  private Blockchain mockBlockchain;

  @Before
  public void setUp() {
    mockAccountService = mock(AccountService.class);
    mockBlockchain = mock(Blockchain.class);

    t = new GetAccountRecentTransactions(mockAccountService, mockBlockchain);
  }

  @Test
  public void processRequest() {
    final String accountId = TestConstants.TEST_ACCOUNT_NUMERIC_ID;

    final JsonObject request = QuickMocker.jsonObject(new JSONParam("account", new JsonPrimitive(accountId)));

    final Peer peerMock = mock(Peer.class);

    final Account mockAccount = mock(Account.class);

    final Transaction mockTransaction = mock(Transaction.class);
    when(mockTransaction.getType()).thenReturn(DigitalGoods.DELISTING);
    final Collection<Transaction> transactionsIterator = mockCollection(mockTransaction);

    when(mockAccountService.getAccount(eq(TestConstants.TEST_ACCOUNT_NUMERIC_ID_PARSED))).thenReturn(mockAccount);
    when(mockBlockchain.getTransactions(eq(mockAccount), eq(0), eq((byte) -1), eq((byte) 0), eq(0), eq(0), eq(9), eq(false))).thenReturn(transactionsIterator);

    final JsonObject result = (JsonObject) t.processRequest(request, peerMock);
    assertNotNull(result);

    final JsonArray transactionsResult = (JsonArray) result.get("transactions");
    assertNotNull(transactionsResult);
    assertEquals(1, transactionsResult.size());
  }

}
