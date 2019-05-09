package brs.http;

import brs.Asset;
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
import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAskOrdersTest extends AbstractUnitTest {

  private ParameterService parameterServiceMock;
  private AssetExchange assetExchangeMock;

  private GetAskOrders t;

  @Before
  public void setUp() {
    parameterServiceMock = mock(ParameterService.class);
    assetExchangeMock = mock(AssetExchange.class);

    t = new GetAskOrders(parameterServiceMock, assetExchangeMock);
  }

  @Test
  public void processRequest() throws BurstException {
    final long assetIndex = 5;
    final int firstIndex = 1;
    final int lastIndex = 3;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
      new MockParam(ASSET_PARAMETER, assetIndex),
      new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
      new MockParam(LAST_INDEX_PARAMETER, lastIndex)
    );

    final Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(assetIndex);

    when(parameterServiceMock.getAsset(eq(req))).thenReturn(asset);

    final Ask askOrder1 = mock(Ask.class);
    when(askOrder1.getId()).thenReturn(3L);
    when(askOrder1.getAssetId()).thenReturn(assetIndex);
    when(askOrder1.getQuantityQNT()).thenReturn(56L);
    when(askOrder1.getPriceNQT()).thenReturn(45L);
    when(askOrder1.getHeight()).thenReturn(32);

    final Ask askOrder2 = mock(Ask.class);
    when(askOrder1.getId()).thenReturn(4L);

    final Collection<Ask> askIterator = this.mockCollection(askOrder1, askOrder2);

    when(assetExchangeMock.getSortedAskOrders(eq(assetIndex), eq(firstIndex), eq(lastIndex))).thenReturn(askIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray orders = (JsonArray) result.get(ASK_ORDERS_RESPONSE);
    assertNotNull(orders);

    assertEquals(2, orders.size());

    final JsonObject askOrder1Result = (JsonObject) orders.get(0);

    assertEquals("" + askOrder1.getId(), JSON.getAsString(askOrder1Result.get(ORDER_RESPONSE)));
    assertEquals("" + askOrder1.getAssetId(), JSON.getAsString(askOrder1Result.get(ASSET_RESPONSE)));
    assertEquals("" + askOrder1.getQuantityQNT(), JSON.getAsString(askOrder1Result.get(QUANTITY_QNT_RESPONSE)));
    assertEquals("" + askOrder1.getPriceNQT(), JSON.getAsString(askOrder1Result.get(PRICE_NQT_RESPONSE)));
    assertEquals(askOrder1.getHeight(), JSON.getAsInt(askOrder1Result.get(HEIGHT_RESPONSE)));
  }
}
