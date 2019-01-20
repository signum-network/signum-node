package brs.http;

import brs.BurstException;
import brs.Order.Ask;
import brs.assetexchange.AssetExchange;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.UNKNOWN_ORDER;
import static brs.http.common.Parameters.ORDER_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetAskOrderTest {

  private GetAskOrder t;

  private AssetExchange mockAssetExchange;

  @Before
  public void setUp() {
    mockAssetExchange = mock(AssetExchange.class);

    t = new GetAskOrder(mockAssetExchange);
  }

  @Test
  public void processRequest() throws BurstException {
    final long orderId = 123L;

    final Ask mockOrder = mock(Ask.class);

    when(mockAssetExchange.getAskOrder(eq(orderId))).thenReturn(mockOrder);

    final HttpServletRequest req = QuickMocker.httpServletRequest(
      new MockParam(ORDER_PARAMETER, orderId)
    );

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);
  }

  @Test
  public void processRequest_unknownOrder() throws BurstException {
    final long orderId = 123L;

    when(mockAssetExchange.getAskOrder(eq(orderId))).thenReturn(null);

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ORDER_PARAMETER, orderId)
    );

    assertEquals(UNKNOWN_ORDER, t.processRequest(req));
  }

}
