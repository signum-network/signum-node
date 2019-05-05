package brs.http;

import brs.Order.Ask;
import brs.assetexchange.AssetExchange;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static brs.http.common.Parameters.FIRST_INDEX_PARAMETER;
import static brs.http.common.Parameters.LAST_INDEX_PARAMETER;
import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAllOpenAskOrdersTest extends AbstractUnitTest {

  private GetAllOpenAskOrders t;

  private AssetExchange mockAssetExchange;

  @Before
  public void setUp() {
    mockAssetExchange = mock(AssetExchange.class);

    t = new GetAllOpenAskOrders(mockAssetExchange);
  }

  @Test
  public void processRequest() {
    final Ask mockAskOrder = mock(Ask.class);
    when(mockAskOrder.getId()).thenReturn(1L);
    when(mockAskOrder.getAssetId()).thenReturn(2L);
    when(mockAskOrder.getQuantityQNT()).thenReturn(3L);
    when(mockAskOrder.getPriceNQT()).thenReturn(4L);
    when(mockAskOrder.getHeight()).thenReturn(5);

    final int firstIndex = 1;
    final int lastIndex = 2;

    final Collection<Ask> mockIterator = mockCollection(mockAskOrder);
    when(mockAssetExchange.getAllAskOrders(eq(firstIndex), eq(lastIndex)))
        .thenReturn(mockIterator);

    final JsonObject result = (JsonObject) t.processRequest(QuickMocker.httpServletRequest(
        new MockParam(FIRST_INDEX_PARAMETER, "" + firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, "" + lastIndex)
    ));

    assertNotNull(result);
    final JsonArray openOrdersResult = (JsonArray) result.get(OPEN_ORDERS_RESPONSE);

    assertNotNull(openOrdersResult);
    assertEquals(1, openOrdersResult.size());

    final JsonObject openOrderResult = (JsonObject) openOrdersResult.get(0);
    assertEquals("" + mockAskOrder.getId(), JSON.getAsString(openOrderResult.get(ORDER_RESPONSE)));
    assertEquals("" + mockAskOrder.getAssetId(), JSON.getAsString(openOrderResult.get(ASSET_RESPONSE)));
    assertEquals("" + mockAskOrder.getQuantityQNT(), JSON.getAsString(openOrderResult.get(QUANTITY_QNT_RESPONSE)));
    assertEquals("" + mockAskOrder.getPriceNQT(), JSON.getAsString(openOrderResult.get(PRICE_NQT_RESPONSE)));
    assertEquals(mockAskOrder.getHeight(), JSON.getAsInt(openOrderResult.get(HEIGHT_RESPONSE)));
  }
}
