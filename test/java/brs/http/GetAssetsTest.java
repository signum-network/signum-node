package brs.http;

import brs.Asset;
import brs.assetexchange.AssetExchange;
import brs.common.QuickMocker;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.INCORRECT_ASSET;
import static brs.http.JSONResponses.UNKNOWN_ASSET;
import static brs.http.common.Parameters.ASSETS_PARAMETER;
import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAssetsTest {

  private GetAssets t;

  private AssetExchange mockAssetExchange;

  @Before
  public void setUp() {
    mockAssetExchange = mock(AssetExchange.class);

    t = new GetAssets(mockAssetExchange);
  }

  @Test
  public void processRequest() {
    final long assetId = 123L;

    final HttpServletRequest req = QuickMocker.httpServletRequest();
    when(req.getParameterValues(eq(ASSETS_PARAMETER))).thenReturn(new String[]{"" + assetId, ""});

    final int mockTradeCount = 1;
    final int mockTransferCount = 2;
    final int mockAccountsCount = 3;

    final Asset mockAsset = mock(Asset.class);
    when(mockAsset.getId()).thenReturn(assetId);

    when(mockAssetExchange.getAsset(eq(assetId))).thenReturn(mockAsset);

    when(mockAssetExchange.getTradeCount(eq(assetId))).thenReturn(mockTradeCount);
    when(mockAssetExchange.getTransferCount(eq(assetId))).thenReturn(mockTransferCount);
    when(mockAssetExchange.getAssetAccountsCount(eq(assetId))).thenReturn(mockAccountsCount);

    final JsonObject response = (JsonObject) t.processRequest(req);
    assertNotNull(response);

    final JsonArray responseList = (JsonArray) response.get(ASSETS_RESPONSE);
    assertNotNull(responseList);
    assertEquals(1, responseList.size());

    final JsonObject assetResponse = (JsonObject) responseList.get(0);
    assertNotNull(assetResponse);
    assertEquals(mockTradeCount, JSON.getAsInt(assetResponse.get(NUMBER_OF_TRADES_RESPONSE)));
    assertEquals(mockTransferCount, JSON.getAsInt(assetResponse.get(NUMBER_OF_TRANSFERS_RESPONSE)));
    assertEquals(mockAccountsCount, JSON.getAsInt(assetResponse.get(NUMBER_OF_ACCOUNTS_RESPONSE)));
  }

  @Test
  public void processRequest_unknownAsset() {
    final long assetId = 123L;

    final HttpServletRequest req = QuickMocker.httpServletRequest();
    when(req.getParameterValues(eq(ASSETS_PARAMETER))).thenReturn(new String[]{"" + assetId});

    when(mockAssetExchange.getAsset(eq(assetId))).thenReturn(null);

    assertEquals(UNKNOWN_ASSET, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectAsset() {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    when(req.getParameterValues(eq(ASSETS_PARAMETER))).thenReturn(new String[]{"unParsable"});

    assertEquals(INCORRECT_ASSET, t.processRequest(req));
  }

}
