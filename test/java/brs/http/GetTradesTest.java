package brs.http;

import brs.Account;
import brs.Asset;
import brs.BurstException;
import brs.Trade;
import brs.assetexchange.AssetExchange;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.services.ParameterService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.TRADES_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetTradesTest extends AbstractUnitTest {

  private GetTrades t;

  private ParameterService mockParameterService;
  private AssetExchange mockAssetExchange;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);
    mockAssetExchange = mock(AssetExchange.class);

    t = new GetTrades(mockParameterService, mockAssetExchange);
  }

  @Test
  public void processRequest_withAssetId() throws BurstException {
    final long assetId = 123L;
    final int firstIndex = 0;
    final int lastIndex = 1;
    final boolean includeAssetInfo = true;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ASSET_PARAMETER, assetId),
        new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, lastIndex),
        new MockParam(INCLUDE_ASSET_INFO_PARAMETER, includeAssetInfo)
    );

    final Asset mockAsset = mock(Asset.class);
    when(mockAsset.getId()).thenReturn(assetId);

    final Trade mockTrade = mock(Trade.class);
    final Collection<Trade> mockTradesIterator = mockCollection(mockTrade);

    when(mockParameterService.getAsset(eq(req))).thenReturn(mockAsset);
    when(mockAssetExchange.getTrades(eq(assetId), eq(firstIndex), eq(lastIndex))).thenReturn(mockTradesIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray trades = (JsonArray) result.get(TRADES_RESPONSE);
    assertNotNull(trades);
    assertEquals(1, trades.size());

    final JsonObject tradeResult = (JsonObject) trades.get(0);
    assertNotNull(tradeResult);
  }

  @Test
  public void processRequest_withAccountId() throws BurstException {
    final long accountId = 321L;
    final int firstIndex = 0;
    final int lastIndex = 1;
    final boolean includeAssetInfo = true;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ACCOUNT_PARAMETER, accountId),
        new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, lastIndex),
        new MockParam(INCLUDE_ASSET_INFO_PARAMETER, includeAssetInfo)
    );

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(accountId);

    final Trade mockTrade = mock(Trade.class);
    final Collection<Trade> mockTradesIterator = mockCollection(mockTrade);

    when(mockParameterService.getAccount(eq(req))).thenReturn(mockAccount);
    when(mockAssetExchange.getAccountTrades(eq(accountId), eq(firstIndex), eq(lastIndex))).thenReturn(mockTradesIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray trades = (JsonArray) result.get(TRADES_RESPONSE);
    assertNotNull(trades);
    assertEquals(1, trades.size());

    final JsonObject tradeResult = (JsonObject) trades.get(0);
    assertNotNull(tradeResult);
  }

  @Test
  public void processRequest_withAssetIdAndAccountId() throws BurstException {
    final long assetId = 123L;
    final long accountId = 321L;
    final int firstIndex = 0;
    final int lastIndex = 1;
    final boolean includeAssetInfo = true;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ASSET_PARAMETER, assetId),
        new MockParam(ACCOUNT_PARAMETER, accountId),
        new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, lastIndex),
        new MockParam(INCLUDE_ASSET_INFO_PARAMETER, includeAssetInfo)
    );

    final Asset mockAsset = mock(Asset.class);
    when(mockAsset.getId()).thenReturn(assetId);

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(accountId);

    final Trade mockTrade = mock(Trade.class);
    final Collection<Trade> mockTradesIterator = mockCollection(mockTrade);

    when(mockParameterService.getAsset(eq(req))).thenReturn(mockAsset);
    when(mockParameterService.getAccount(eq(req))).thenReturn(mockAccount);
    when(mockAssetExchange.getAccountAssetTrades(eq(accountId), eq(assetId), eq(firstIndex), eq(lastIndex))).thenReturn(mockTradesIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray trades = (JsonArray) result.get(TRADES_RESPONSE);
    assertNotNull(trades);
    assertEquals(1, trades.size());

    final JsonObject tradeResult = (JsonObject) trades.get(0);
    assertNotNull(tradeResult);
  }
}
