package brs.http;

import brs.BurstException;
import brs.DigitalGoodsStore.Purchase;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.services.DGSGoodsStoreService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.http.JSONResponses.MISSING_SELLER;
import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.PURCHASES_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetDGSPendingPurchasesTest extends AbstractUnitTest {

  private GetDGSPendingPurchases t;

  private DGSGoodsStoreService mockDGSGoodStoreService;

  @Before
  public void setUp() {
    mockDGSGoodStoreService = mock(DGSGoodsStoreService.class);

    t = new GetDGSPendingPurchases(mockDGSGoodStoreService);
  }

  @Test
  public void processRequest() throws BurstException {
    final long sellerId = 123L;
    final int firstIndex = 1;
    final int lastIndex = 2;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(SELLER_PARAMETER, sellerId),
        new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, lastIndex)
    );

    final Purchase mockPurchase = mock(Purchase.class);

    final Collection<Purchase> mockPurchaseIterator = mockCollection(mockPurchase);
    when(mockDGSGoodStoreService.getPendingSellerPurchases(eq(sellerId), eq(firstIndex), eq(lastIndex))).thenReturn(mockPurchaseIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray resultPurchases = (JsonArray) result.get(PURCHASES_RESPONSE);

    assertNotNull(resultPurchases);
    assertEquals(1, resultPurchases.size());
  }

  @Test
  public void processRequest_missingSeller() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(SELLER_PARAMETER, 0)
    );

    assertEquals(MISSING_SELLER, t.processRequest(req));
  }

}
