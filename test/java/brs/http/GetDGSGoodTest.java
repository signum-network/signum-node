package brs.http;

import brs.BurstException;
import brs.DigitalGoodsStore;
import brs.common.QuickMocker;
import brs.services.ParameterService;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetDGSGoodTest {

  private GetDGSGood t;

  private ParameterService mockParameterService;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);

    t = new GetDGSGood(mockParameterService);
  }

  @Test
  public void processRequest() throws BurstException {
    final DigitalGoodsStore.Goods mockGoods = mock(DigitalGoodsStore.Goods.class);
    when(mockGoods.getId()).thenReturn(1L);
    when(mockGoods.getName()).thenReturn("name");
    when(mockGoods.getDescription()).thenReturn("description");
    when(mockGoods.getQuantity()).thenReturn(2);
    when(mockGoods.getPriceNQT()).thenReturn(3L);
    when(mockGoods.getTags()).thenReturn("tags");
    when(mockGoods.isDelisted()).thenReturn(true);
    when(mockGoods.getTimestamp()).thenReturn(12345);

    final HttpServletRequest req = QuickMocker.httpServletRequest();

    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    assertEquals("" + mockGoods.getId(), result.get(GOODS_RESPONSE));
    assertEquals(mockGoods.getName(), result.get(NAME_RESPONSE));
    assertEquals(mockGoods.getDescription(), result.get(DESCRIPTION_RESPONSE));
    assertEquals(mockGoods.getQuantity(), result.get(QUANTITY_RESPONSE));
    assertEquals("" + mockGoods.getPriceNQT(), result.get(PRICE_NQT_RESPONSE));
    assertEquals(mockGoods.getTags(), result.get(TAGS_RESPONSE));
    assertEquals(mockGoods.isDelisted(), result.get(DELISTED_RESPONSE));
    assertEquals(mockGoods.getTimestamp(), result.get(TIMESTAMP_RESPONSE));
  }
}