package brs.http;

import brs.Account;
import brs.BurstException;
import brs.Order.Bid;
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
import static brs.http.common.ResultFields.BID_ORDERS_RESPONSE;
import static brs.http.common.ResultFields.ORDER_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAccountCurrentBidOrdersTest extends AbstractUnitTest {

  private GetAccountCurrentBidOrders t;

  private ParameterService mockParameterService;
  private AssetExchange mockAssetExchange;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);
    mockAssetExchange = mock(AssetExchange.class);

    t = new GetAccountCurrentBidOrders(mockParameterService, mockAssetExchange);
  }

  @Test
  public void processRequest_byAccount() throws BurstException {
    final long accountId = 123L;
    final int firstIndex = 0;
    final int lastIndex = 1;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
      new MockParam(ACCOUNT_PARAMETER, accountId),
      new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
      new MockParam(LAST_INDEX_PARAMETER, lastIndex)
    );

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(accountId);

    final long mockBidId = 456L;
    final Bid bid = mock(Bid.class);
    when(bid.getId()).thenReturn(mockBidId);

    final Collection<Bid> mockBidIterator = mockCollection(bid);

    when(mockParameterService.getAccount(eq(req))).thenReturn(mockAccount);
    when(mockAssetExchange.getBidOrdersByAccount(eq(accountId), eq(firstIndex), eq(lastIndex))).thenReturn(mockBidIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray resultList = (JsonArray) result.get(BID_ORDERS_RESPONSE);
    assertNotNull(resultList);
    assertEquals(1, resultList.size());

    final JsonObject resultBid = (JsonObject) resultList.get(0);
    assertNotNull(resultBid);
    assertEquals("" + mockBidId, JSON.getAsString(resultBid.get(ORDER_RESPONSE)));
  }

  @Test
  public void processRequest_byAccountAsset() throws BurstException {
    final long accountId = 123L;
    final long assetId = 234L;
    final int firstIndex = 0;
    final int lastIndex = 1;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ACCOUNT_PARAMETER, accountId),
        new MockParam(ASSET_PARAMETER, assetId),
        new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, lastIndex)
    );

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(accountId);

    final long mockBidId = 456L;
    final Bid bid = mock(Bid.class);
    when(bid.getId()).thenReturn(mockBidId);

    final Collection<Bid> mockBidIterator = mockCollection(bid);

    when(mockParameterService.getAccount(eq(req))).thenReturn(mockAccount);
    when(mockAssetExchange.getBidOrdersByAccountAsset(eq(accountId), eq(assetId), eq(firstIndex), eq(lastIndex))).thenReturn(mockBidIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray resultList = (JsonArray) result.get(BID_ORDERS_RESPONSE);
    assertNotNull(resultList);
    assertEquals(1, resultList.size());

    final JsonObject resultBid = (JsonObject) resultList.get(0);
    assertNotNull(resultBid);
    assertEquals("" + mockBidId, JSON.getAsString(resultBid.get(ORDER_RESPONSE)));
  }

}
