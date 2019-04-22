package brs.http;

import brs.Asset;
import brs.BurstException;
import brs.assetexchange.AssetExchange;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.services.ParameterService;
import brs.util.JSON;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.ASSET_PARAMETER;
import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetAssetTest extends AbstractUnitTest {

  private ParameterService parameterServiceMock;
  private AssetExchange mockAssetExchange;

  private GetAsset t;

  @Before
  public void setUp() {
    parameterServiceMock = mock(ParameterService.class);
    mockAssetExchange = mock(AssetExchange.class);

    t = new GetAsset(parameterServiceMock, mockAssetExchange);
  }

  @Test
  public void processRequest() throws BurstException {
    final long assetId = 4;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ASSET_PARAMETER, assetId)
    );

    final Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(assetId);
    when(asset.getName()).thenReturn("assetName");
    when(asset.getDescription()).thenReturn("assetDescription");
    when(asset.getDecimals()).thenReturn(new Byte("3"));

    when(parameterServiceMock.getAsset(eq(req))).thenReturn(asset);

    int tradeCount = 1;
    int transferCount = 2;
    int assetAccountsCount = 3;

    when(mockAssetExchange.getTradeCount(eq(assetId))).thenReturn(tradeCount);
    when(mockAssetExchange.getTransferCount(eq(assetId))).thenReturn(transferCount);
    when(mockAssetExchange.getAssetAccountsCount(eq(assetId))).thenReturn(assetAccountsCount);

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertNotNull(result);
    assertEquals(asset.getName(), JSON.getAsString(result.get(NAME_RESPONSE)));
    assertEquals(asset.getDescription(), JSON.getAsString(result.get(DESCRIPTION_RESPONSE)));
    assertEquals(asset.getDecimals(), JSON.getAsInt(result.get(DECIMALS_RESPONSE)));
    assertEquals("" + asset.getQuantityQNT(), JSON.getAsString(result.get(QUANTITY_QNT_RESPONSE)));
    assertEquals("" + asset.getId(), JSON.getAsString(result.get(ASSET_RESPONSE)));
    assertEquals(tradeCount, JSON.getAsInt(result.get(NUMBER_OF_TRADES_RESPONSE)));
    assertEquals(transferCount, JSON.getAsInt(result.get(NUMBER_OF_TRANSFERS_RESPONSE)));
    assertEquals(assetAccountsCount, JSON.getAsInt(result.get(NUMBER_OF_ACCOUNTS_RESPONSE)));
  }
}