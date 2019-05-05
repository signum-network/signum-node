package brs.http;

import brs.Account;
import brs.Account.AccountAsset;
import brs.BurstException;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.services.AccountService;
import brs.services.ParameterService;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

;

public class GetAccountTest extends AbstractUnitTest {

  private GetAccount t;

  private ParameterService parameterServiceMock;
  private AccountService accountServiceMock;

  @Before
  public void setUp() {
    parameterServiceMock = mock(ParameterService.class);
    accountServiceMock = mock(AccountService.class);

    t = new GetAccount(parameterServiceMock, accountServiceMock);
  }

  @Test
  public void processRequest() throws BurstException {
    final long mockAccountId = 123L;
    final String mockAccountName = "accountName";
    final String mockAccountDescription = "accountDescription";

    final long mockAssetId = 321L;
    final long balanceNQT = 23L;
    final long mockUnconfirmedQuantityNQT = 12L;

    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(mockAccountId);
    when(mockAccount.getPublicKey()).thenReturn(new byte[]{(byte) 1});
    when(mockAccount.getName()).thenReturn(mockAccountName);
    when(mockAccount.getDescription()).thenReturn(mockAccountDescription);

    when(parameterServiceMock.getAccount(eq(req))).thenReturn(mockAccount);

    final AccountAsset mockAccountAsset = mock(AccountAsset.class);
    when(mockAccountAsset.getAssetId()).thenReturn(mockAssetId);
    when(mockAccountAsset.getUnconfirmedQuantityQNT()).thenReturn(mockUnconfirmedQuantityNQT);
    when(mockAccountAsset.getQuantityQNT()).thenReturn(balanceNQT);
    Collection<AccountAsset> mockAssetOverview = mockCollection(mockAccountAsset);
    when(accountServiceMock.getAssets(eq(mockAccountId), eq(0), eq(-1))).thenReturn(mockAssetOverview);

    final JsonObject response = (JsonObject) t.processRequest(req);
    assertEquals("01", JSON.getAsString(response.get(PUBLIC_KEY_RESPONSE)));
    assertEquals(mockAccountName, JSON.getAsString(response.get(NAME_RESPONSE)));
    assertEquals(mockAccountDescription, JSON.getAsString(response.get(DESCRIPTION_RESPONSE)));

    final JsonArray confirmedBalanceResponses = (JsonArray) response.get(ASSET_BALANCES_RESPONSE);
    assertNotNull(confirmedBalanceResponses);
    assertEquals(1, confirmedBalanceResponses.size());
    final JsonObject balanceResponse = (JsonObject) confirmedBalanceResponses.get(0);
    assertEquals("" + mockAssetId, JSON.getAsString(balanceResponse.get(ASSET_RESPONSE)));
    assertEquals("" + balanceNQT, JSON.getAsString(balanceResponse.get(BALANCE_QNT_RESPONSE)));

    final JsonArray unconfirmedBalanceResponses = (JsonArray) response.get(UNCONFIRMED_ASSET_BALANCES_RESPONSE);
    assertNotNull(unconfirmedBalanceResponses);
    assertEquals(1, unconfirmedBalanceResponses.size());
    final JsonObject unconfirmedBalanceResponse = (JsonObject) unconfirmedBalanceResponses.get(0);
    assertEquals("" + mockAssetId, JSON.getAsString(unconfirmedBalanceResponse.get(ASSET_RESPONSE)));
    assertEquals("" + mockUnconfirmedQuantityNQT, JSON.getAsString(unconfirmedBalanceResponse.get(UNCONFIRMED_BALANCE_QNT_RESPONSE)));
  }
}
