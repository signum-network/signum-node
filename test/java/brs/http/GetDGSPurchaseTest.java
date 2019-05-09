package brs.http;

import brs.BurstException;
import brs.DigitalGoodsStore.Purchase;
import brs.common.QuickMocker;
import brs.crypto.EncryptedData;
import brs.services.ParameterService;
import brs.util.JSON;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetDGSPurchaseTest {

  private GetDGSPurchase t;

  private ParameterService mockParameterService;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);

    t = new GetDGSPurchase(mockParameterService);
  }


  @Test
  public void processRequest() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final EncryptedData mockEncryptedData = mock(EncryptedData.class);

    when(mockEncryptedData.getData()).thenReturn(new byte[]{(byte) 1});
    when(mockEncryptedData.getNonce()).thenReturn(new byte[]{(byte) 1});

    final List<EncryptedData> mockEncryptedDataList = Arrays.asList(mockEncryptedData);

    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.getId()).thenReturn(1L);
    when(mockPurchase.getGoodsId()).thenReturn(2L);
    when(mockPurchase.getName()).thenReturn("name");
    when(mockPurchase.getSellerId()).thenReturn(3L);
    when(mockPurchase.getPriceNQT()).thenReturn(4L);
    when(mockPurchase.getQuantity()).thenReturn(5);
    when(mockPurchase.getBuyerId()).thenReturn(6L);
    when(mockPurchase.getTimestamp()).thenReturn(7);
    when(mockPurchase.getDeliveryDeadlineTimestamp()).thenReturn(8);
    when(mockPurchase.isPending()).thenReturn(true);
    when(mockPurchase.goodsIsText()).thenReturn(true);
    when(mockPurchase.getDiscountNQT()).thenReturn(8L);
    when(mockPurchase.getRefundNQT()).thenReturn(9L);
    when(mockPurchase.getEncryptedGoods()).thenReturn(mockEncryptedData);
    when(mockPurchase.getFeedbackNotes()).thenReturn(mockEncryptedDataList);
    when(mockPurchase.getRefundNote()).thenReturn(mockEncryptedData);
    when(mockPurchase.getNote()).thenReturn(mockEncryptedData);
    when(mockPurchase.getPublicFeedback()).thenReturn(Arrays.asList("feedback"));

    when(mockParameterService.getPurchase(eq(req))).thenReturn(mockPurchase);

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertNotNull(result);

    assertEquals("" + mockPurchase.getId(), JSON.getAsString(result.get(PURCHASE_RESPONSE)));
    assertEquals("" + mockPurchase.getGoodsId(), JSON.getAsString(result.get(GOODS_RESPONSE)));
    assertEquals(mockPurchase.getName(), JSON.getAsString(result.get(NAME_RESPONSE)));
    assertEquals("" + mockPurchase.getSellerId(), JSON.getAsString(result.get(SELLER_RESPONSE)));
    assertEquals("" + mockPurchase.getPriceNQT(), JSON.getAsString(result.get(PRICE_NQT_RESPONSE)));
    assertEquals(mockPurchase.getQuantity(), JSON.getAsInt(result.get(QUANTITY_RESPONSE)));
    assertEquals("" + mockPurchase.getBuyerId(), JSON.getAsString(result.get(BUYER_RESPONSE)));
    assertEquals(mockPurchase.getTimestamp(), JSON.getAsInt(result.get(TIMESTAMP_RESPONSE)));
    assertEquals(mockPurchase.getDeliveryDeadlineTimestamp(), JSON.getAsInt(result.get(DELIVERY_DEADLINE_TIMESTAMP_RESPONSE)));
    assertEquals(mockPurchase.isPending(), JSON.getAsBoolean(result.get(PENDING_RESPONSE)));
    assertEquals("" + mockPurchase.getDiscountNQT(), JSON.getAsString(result.get(DISCOUNT_NQT_RESPONSE)));
    assertEquals("" + mockPurchase.getRefundNQT(), JSON.getAsString(result.get(REFUND_NQT_RESPONSE)));
  }

}
