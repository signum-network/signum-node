package brs.http;

import brs.Order.Bid;
import brs.assetexchange.AssetExchange;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.db.BurstIterator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import static brs.http.common.Parameters.FIRST_INDEX_PARAMETER;
import static brs.http.common.Parameters.LAST_INDEX_PARAMETER;
import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAllOpenBidOrdersTest extends AbstractUnitTest {

  private GetAllOpenBidOrders t;

  private AssetExchange mockAssetExchange;

  @Before
  public void setUp() {
    mockAssetExchange = mock(AssetExchange.class);

    t = new GetAllOpenBidOrders(mockAssetExchange);
  }

  @Test
  public void processRequest() {
    final Bid mockBidOrder = mock(Bid.class);
    when(mockBidOrder.getId()).thenReturn(1L);
    when(mockBidOrder.getAssetId()).thenReturn(2L);
    when(mockBidOrder.getQuantityQNT()).thenReturn(3L);
    when(mockBidOrder.getPriceNQT()).thenReturn(4L);
    when(mockBidOrder.getHeight()).thenReturn(5);

    final int firstIndex = 1;
    final int lastIndex = 2;

    final BurstIterator<Bid> mockIterator = mockBurstIterator(mockBidOrder);
    when(mockAssetExchange.getAllBidOrders(eq(firstIndex), eq(lastIndex)))
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
    assertEquals("" + mockBidOrder.getId(), openOrderResult.get(ORDER_RESPONSE));
    assertEquals("" + mockBidOrder.getAssetId(), openOrderResult.get(ASSET_RESPONSE));
    assertEquals("" + mockBidOrder.getQuantityQNT(), openOrderResult.get(QUANTITY_QNT_RESPONSE));
    assertEquals("" + mockBidOrder.getPriceNQT(), openOrderResult.get(PRICE_NQT_RESPONSE));
    assertEquals(mockBidOrder.getHeight(), openOrderResult.get(HEIGHT_RESPONSE));
  }
}
