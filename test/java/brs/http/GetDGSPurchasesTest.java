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

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.PURCHASES_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetDGSPurchasesTest extends AbstractUnitTest {

  private GetDGSPurchases t;

  private DGSGoodsStoreService mockDGSGoodsStoreService;

  @Before
  public void setUp() {
    mockDGSGoodsStoreService = mock(DGSGoodsStoreService.class);

    t = new GetDGSPurchases(mockDGSGoodsStoreService);
  }

  @Test
  public void processRequest_getAllPurchases() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(SELLER_PARAMETER, 0),
        new MockParam(BUYER_PARAMETER, 0),
        new MockParam(FIRST_INDEX_PARAMETER, 0),
        new MockParam(LAST_INDEX_PARAMETER, -1),
        new MockParam(COMPLETED_PARAMETER, false)
    );

    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.isPending()).thenReturn(false);

    Collection<Purchase> mockGoodsIterator = mockCollection(mockPurchase);

    when(mockDGSGoodsStoreService.getAllPurchases(eq(0), eq(-1))).thenReturn(mockGoodsIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray purchasesResult = (JsonArray) result.get(PURCHASES_RESPONSE);
    assertNotNull(purchasesResult);
    assertEquals(1, purchasesResult.size());
  }

  @Test
  public void processRequest_getSellerPurchases() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(SELLER_PARAMETER, 1),
        new MockParam(BUYER_PARAMETER, 0),
        new MockParam(FIRST_INDEX_PARAMETER, 0),
        new MockParam(LAST_INDEX_PARAMETER, -1),
        new MockParam(COMPLETED_PARAMETER, false)
    );

    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.isPending()).thenReturn(false);

    Collection<Purchase> mockGoodsIterator = mockCollection(mockPurchase);

    when(mockDGSGoodsStoreService.getSellerPurchases(eq(1L), eq(0), eq(-1))).thenReturn(mockGoodsIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray purchasesResult = (JsonArray) result.get(PURCHASES_RESPONSE);
    assertNotNull(purchasesResult);
    assertEquals(1, purchasesResult.size());
  }

  @Test
  public void processRequest_getBuyerPurchases() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(SELLER_PARAMETER, 0),
        new MockParam(BUYER_PARAMETER, 1),
        new MockParam(FIRST_INDEX_PARAMETER, 0),
        new MockParam(LAST_INDEX_PARAMETER, -1),
        new MockParam(COMPLETED_PARAMETER, false)
    );

    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.isPending()).thenReturn(false);

    Collection<Purchase> mockGoodsIterator = mockCollection(mockPurchase);

    when(mockDGSGoodsStoreService.getBuyerPurchases(eq(1L), eq(0), eq(-1))).thenReturn(mockGoodsIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray purchasesResult = (JsonArray) result.get(PURCHASES_RESPONSE);
    assertNotNull(purchasesResult);
    assertEquals(1, purchasesResult.size());
  }

  @Test
  public void processRequest_getSellerBuyerPurchases() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(SELLER_PARAMETER, 1),
        new MockParam(BUYER_PARAMETER, 2),
        new MockParam(FIRST_INDEX_PARAMETER, 0),
        new MockParam(LAST_INDEX_PARAMETER, -1),
        new MockParam(COMPLETED_PARAMETER, false)
    );

    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.isPending()).thenReturn(false);

    Collection<Purchase> mockGoodsIterator = mockCollection(mockPurchase);

    when(mockDGSGoodsStoreService.getSellerBuyerPurchases(eq(1L), eq(2L), eq(0), eq(-1))).thenReturn(mockGoodsIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray purchasesResult = (JsonArray) result.get(PURCHASES_RESPONSE);
    assertNotNull(purchasesResult);
    assertEquals(1, purchasesResult.size());
  }

}
