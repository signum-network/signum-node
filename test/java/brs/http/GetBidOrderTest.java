package brs.http;

import brs.BurstException;
import brs.Order.Bid;
import brs.assetexchange.AssetExchange;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.util.JSON;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.UNKNOWN_ORDER;
import static brs.http.common.Parameters.ORDER_PARAMETER;
import static brs.http.common.ResultFields.ORDER_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetBidOrderTest {

  private GetBidOrder t;

  private AssetExchange mockAssetExchange;

  @Before
  public void setUp() {
    mockAssetExchange = mock(AssetExchange.class);

    t = new GetBidOrder(mockAssetExchange);
  }

  @Test
  public void processRequest() throws BurstException {
    final long bidOrderId = 123L;
    Bid mockBid = mock(Bid.class);
    when(mockBid.getId()).thenReturn(bidOrderId);

    when(mockAssetExchange.getBidOrder(eq(bidOrderId))).thenReturn(mockBid);

    HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(ORDER_PARAMETER, bidOrderId));

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);
    assertEquals("" + bidOrderId, JSON.getAsString(result.get(ORDER_RESPONSE)));
  }

  @Test
  public void processRequest_orderNotFoundUnknownOrder() throws BurstException {
    final long bidOrderId = 123L;

    HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(ORDER_PARAMETER, bidOrderId));

    assertEquals(UNKNOWN_ORDER, t.processRequest(req));
  }

}
