package brs.http;

import brs.Account;
import brs.BurstException;
import brs.Order.Ask;
import brs.assetexchange.AssetExchange;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.services.ParameterService;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.ASK_ORDER_IDS_RESPONSE;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAccountCurrentAskOrderIdsTest extends AbstractUnitTest {

  private GetAccountCurrentAskOrderIds t;

  private ParameterService mockParameterService;
  private AssetExchange mockAssetExchange;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);
    mockAssetExchange = mock(AssetExchange.class);

    t = new GetAccountCurrentAskOrderIds(mockParameterService, mockAssetExchange);
  }

  @Test
  public void processRequest_getAskOrdersByAccount() throws BurstException {
    final long accountId = 2L;
    final int firstIndex = 1;
    final int lastIndex = 2;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ACCOUNT_PARAMETER, accountId),
        new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, lastIndex)
    );

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(accountId);
    when(mockParameterService.getAccount(eq(req))).thenReturn(mockAccount);

    final Ask mockAsk = mock(Ask.class);
    when(mockAsk.getId()).thenReturn(1L);

    final Collection<Ask> mockAskIterator = mockCollection(mockAsk);

    when(mockAssetExchange.getAskOrdersByAccount(eq(accountId), eq(firstIndex), eq(lastIndex))).thenReturn(mockAskIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertNotNull(result);

    final JsonArray resultList = (JsonArray) result.get(ASK_ORDER_IDS_RESPONSE);
    assertNotNull(resultList);
    assertEquals(1, (resultList).size());

    assertEquals("" + mockAsk.getId(), JSON.getAsString(resultList.get(0)));
  }

  @Test
  public void processRequest_getAskOrdersByAccountAsset() throws BurstException {
    final long assetId = 1L;
    final long accountId = 2L;
    final int firstIndex = 1;
    final int lastIndex = 2;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ACCOUNT_PARAMETER, accountId),
        new MockParam(ASSET_PARAMETER, assetId),
        new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, lastIndex)
    );

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(accountId);
    when(mockParameterService.getAccount(eq(req))).thenReturn(mockAccount);

    final Ask mockAsk = mock(Ask.class);
    when(mockAsk.getId()).thenReturn(1L);

    final Collection<Ask> mockAskIterator = mockCollection(mockAsk);

    when(mockAssetExchange.getAskOrdersByAccountAsset(eq(accountId), eq(assetId), eq(firstIndex), eq(lastIndex))).thenReturn(mockAskIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertNotNull(result);

    final JsonArray resultList = (JsonArray) result.get(ASK_ORDER_IDS_RESPONSE);
    assertNotNull(resultList);
    assertEquals(1, (resultList).size());

    assertEquals("" + mockAsk.getId(), JSON.getAsString(resultList.get(0)));
  }

}
