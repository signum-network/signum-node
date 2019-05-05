package brs.http;

import brs.Asset;
import brs.BurstException;
import brs.Trade;
import brs.assetexchange.AssetExchange;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

;

public class GetAllTradesTest extends AbstractUnitTest {

  private GetAllTrades t;

  private AssetExchange mockAssetExchange;

  @Before
  public void setUp() {
    mockAssetExchange = mock(AssetExchange.class);

    t = new GetAllTrades(mockAssetExchange);
  }

  @Test
  public void processRequest_withAssetsInformation() throws BurstException {
    final int timestamp = 1;
    final int firstIndex = 0;
    final int lastIndex = 1;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
      new MockParam(TIMESTAMP_PARAMETER, timestamp),
      new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
      new MockParam(LAST_INDEX_PARAMETER, lastIndex),
      new MockParam(INCLUDE_ASSET_INFO_PARAMETER, true)
    );

    final long mockAssetId = 123L;
    final String mockAssetName = "mockAssetName";
    final Asset mockAsset = mock(Asset.class);
    when(mockAsset.getId()).thenReturn(mockAssetId);
    when(mockAsset.getName()).thenReturn(mockAssetName);

    final long priceNQT = 123L;
    final Trade mockTrade = mock(Trade.class);
    when(mockTrade.getPriceNQT()).thenReturn(priceNQT);
    when(mockTrade.getTimestamp()).thenReturn(2);
    when(mockTrade.getAssetId()).thenReturn(mockAssetId);

    final Collection<Trade> mockTradeIterator = mockCollection(mockTrade);

    when(mockAssetExchange.getAllTrades(eq(0), eq(-1))).thenReturn(mockTradeIterator);
    when(mockAssetExchange.getAsset(eq(mockAssetId))).thenReturn(mockAsset);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray tradesResult = (JsonArray) result.get(TRADES_RESPONSE);
    assertNotNull(tradesResult);
    assertEquals(1, tradesResult.size());

    final JsonObject tradeAssetInfoResult = (JsonObject) tradesResult.get(0);
    assertNotNull(tradeAssetInfoResult);

    assertEquals("" + priceNQT, JSON.getAsString(tradeAssetInfoResult.get(PRICE_NQT_RESPONSE)));
    assertEquals("" + mockAssetId, JSON.getAsString(tradeAssetInfoResult.get(ASSET_RESPONSE)));
    assertEquals(mockAssetName, JSON.getAsString(tradeAssetInfoResult.get(NAME_RESPONSE)));
  }

  @Test
  public void processRequest_withoutAssetsInformation() throws BurstException {
    final int timestamp = 1;
    final int firstIndex = 0;
    final int lastIndex = 1;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(TIMESTAMP_PARAMETER, timestamp),
        new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, lastIndex),
        new MockParam(INCLUDE_ASSET_INFO_PARAMETER, false)
    );

    final long mockAssetId = 123L;
    final long priceNQT = 123L;
    final Trade mockTrade = mock(Trade.class);
    when(mockTrade.getPriceNQT()).thenReturn(priceNQT);
    when(mockTrade.getTimestamp()).thenReturn(2);
    when(mockTrade.getAssetId()).thenReturn(mockAssetId);

    final Collection<Trade> mockTradeIterator = mockCollection(mockTrade);

    when(mockAssetExchange.getAllTrades(eq(0), eq(-1))).thenReturn(mockTradeIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray tradesResult = (JsonArray) result.get(TRADES_RESPONSE);
    assertNotNull(tradesResult);
    assertEquals(1, tradesResult.size());

    final JsonObject tradeAssetInfoResult = (JsonObject) tradesResult.get(0);
    assertNotNull(tradeAssetInfoResult);

    assertEquals("" + priceNQT, JSON.getAsString(tradeAssetInfoResult.get(PRICE_NQT_RESPONSE)));
    assertEquals("" + mockAssetId, JSON.getAsString(tradeAssetInfoResult.get(ASSET_RESPONSE)));
    assertNull(JSON.getAsString(tradeAssetInfoResult.get(NAME_RESPONSE)));

    verify(mockAssetExchange, never()).getAsset(eq(mockAssetId));
  }

}
