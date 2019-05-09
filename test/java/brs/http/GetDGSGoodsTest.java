package brs.http;

import brs.BurstException;
import brs.DigitalGoodsStore;
import brs.DigitalGoodsStore.Goods;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.db.sql.DbUtils;
import brs.services.DGSGoodsStoreService;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressStaticInitializationFor("brs.db.sql.DbUtils")
@PrepareForTest(DbUtils.class)
@RunWith(PowerMockRunner.class)
public class GetDGSGoodsTest extends AbstractUnitTest {

  private GetDGSGoods t;

  private DGSGoodsStoreService mockDGSGoodsStoreService;

  @Before
  public void setUp() {
    mockDGSGoodsStoreService = mock(DGSGoodsStoreService.class);

    t = new GetDGSGoods(mockDGSGoodsStoreService);
  }

  @Test
  public void processRequest_getSellerGoods() throws BurstException {
    final long sellerId = 1L;
    final int firstIndex = 2;
    final int lastIndex = 3;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(SELLER_PARAMETER, "" + sellerId),
        new MockParam(FIRST_INDEX_PARAMETER, "" + firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, "" + lastIndex),
        new MockParam(IN_STOCK_ONLY_PARAMETER, "true")
    );

    final Goods mockGood = mockGood();
    final Collection<Goods> mockGoodIterator = mockCollection(mockGood);

    when(mockDGSGoodsStoreService.getSellerGoods(eq(sellerId), eq(true), eq(firstIndex), eq(lastIndex)))
        .thenReturn(mockGoodIterator);

    final JsonObject fullResult = (JsonObject) t.processRequest(req);
    assertNotNull(fullResult);

    final JsonArray goodsList = (JsonArray) fullResult.get(GOODS_RESPONSE);
    assertNotNull(goodsList);
    assertEquals(1, goodsList.size());

    final JsonObject result = (JsonObject) goodsList.get(0);
    assertNotNull(result);

    assertEquals("" + mockGood.getId(), JSON.getAsString(result.get(GOODS_RESPONSE)));
    assertEquals(mockGood.getName(), JSON.getAsString(result.get(NAME_RESPONSE)));
    assertEquals(mockGood.getDescription(), JSON.getAsString(result.get(DESCRIPTION_RESPONSE)));
    assertEquals(mockGood.getQuantity(), JSON.getAsInt(result.get(QUANTITY_RESPONSE)));
    assertEquals("" + mockGood.getPriceNQT(), JSON.getAsString(result.get(PRICE_NQT_RESPONSE)));
    assertEquals("" + mockGood.getSellerId(), JSON.getAsString(result.get(SELLER_PARAMETER)));
    assertEquals(mockGood.getTags(), JSON.getAsString(result.get(TAGS_RESPONSE)));
    assertEquals(mockGood.isDelisted(), JSON.getAsBoolean(result.get(DELISTED_RESPONSE)));
    assertEquals(mockGood.getTimestamp(), JSON.getAsInt(result.get(TIMESTAMP_RESPONSE)));
  }

  @Test
  public void processRequest_getAllGoods() throws BurstException {
    final long sellerId = 0L;
    final int firstIndex = 2;
    final int lastIndex = 3;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(SELLER_PARAMETER, "" + sellerId),
        new MockParam(FIRST_INDEX_PARAMETER, "" + firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, "" + lastIndex),
        new MockParam(IN_STOCK_ONLY_PARAMETER, "false")
    );

    final Goods mockGood = mockGood();
    final Collection<Goods> mockGoodIterator = mockCollection(mockGood);

    when(mockDGSGoodsStoreService.getAllGoods(eq(firstIndex), eq(lastIndex)))
        .thenReturn(mockGoodIterator);

    final JsonObject fullResult = (JsonObject) t.processRequest(req);
    assertNotNull(fullResult);

    final JsonArray goodsList = (JsonArray) fullResult.get(GOODS_RESPONSE);
    assertNotNull(goodsList);
    assertEquals(1, goodsList.size());

    final JsonObject result = (JsonObject) goodsList.get(0);
    assertNotNull(result);

    assertEquals("" + mockGood.getId(), JSON.getAsString(result.get(GOODS_RESPONSE)));
    assertEquals(mockGood.getName(), JSON.getAsString(result.get(NAME_RESPONSE)));
    assertEquals(mockGood.getDescription(), JSON.getAsString(result.get(DESCRIPTION_RESPONSE)));
    assertEquals(mockGood.getQuantity(), JSON.getAsInt(result.get(QUANTITY_RESPONSE)));
    assertEquals("" + mockGood.getPriceNQT(), JSON.getAsString(result.get(PRICE_NQT_RESPONSE)));
    assertEquals("" + mockGood.getSellerId(), JSON.getAsString(result.get(SELLER_PARAMETER)));
    assertEquals(mockGood.getTags(), JSON.getAsString(result.get(TAGS_RESPONSE)));
    assertEquals(mockGood.isDelisted(), JSON.getAsBoolean(result.get(DELISTED_RESPONSE)));
    assertEquals(mockGood.getTimestamp(), JSON.getAsInt(result.get(TIMESTAMP_RESPONSE)));
  }

  @Test
  public void processRequest_getGoodsInStock() throws BurstException {
    final long sellerId = 0L;
    final int firstIndex = 2;
    final int lastIndex = 3;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(SELLER_PARAMETER, "" + sellerId),
        new MockParam(FIRST_INDEX_PARAMETER, "" + firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, "" + lastIndex),
        new MockParam(IN_STOCK_ONLY_PARAMETER, "true")
    );

    final Goods mockGood = mockGood();
    final Collection<Goods> mockGoodIterator = mockCollection(mockGood);

    when(mockDGSGoodsStoreService.getGoodsInStock(eq(firstIndex), eq(lastIndex)))
        .thenReturn(mockGoodIterator);

    final JsonObject fullResult = (JsonObject) t.processRequest(req);
    assertNotNull(fullResult);

    final JsonArray goodsList = (JsonArray) fullResult.get(GOODS_RESPONSE);
    assertNotNull(goodsList);
    assertEquals(1, goodsList.size());

    final JsonObject result = (JsonObject) goodsList.get(0);
    assertNotNull(result);

    assertEquals("" + mockGood.getId(), JSON.getAsString(result.get(GOODS_RESPONSE)));
    assertEquals(mockGood.getName(), JSON.getAsString(result.get(NAME_RESPONSE)));
    assertEquals(mockGood.getDescription(), JSON.getAsString(result.get(DESCRIPTION_RESPONSE)));
    assertEquals(mockGood.getQuantity(), JSON.getAsInt(result.get(QUANTITY_RESPONSE)));
    assertEquals("" + mockGood.getPriceNQT(), JSON.getAsString(result.get(PRICE_NQT_RESPONSE)));
    assertEquals("" + mockGood.getSellerId(), JSON.getAsString(result.get(SELLER_PARAMETER)));
    assertEquals(mockGood.getTags(), JSON.getAsString(result.get(TAGS_RESPONSE)));
    assertEquals(mockGood.isDelisted(), JSON.getAsBoolean(result.get(DELISTED_RESPONSE)));
    assertEquals(mockGood.getTimestamp(), JSON.getAsInt(result.get(TIMESTAMP_RESPONSE)));
  }

  private DigitalGoodsStore.Goods mockGood() {
    final DigitalGoodsStore.Goods mockGood = mock(DigitalGoodsStore.Goods.class);

    when(mockGood.getId()).thenReturn(1L);
    when(mockGood.getName()).thenReturn("name");
    when(mockGood.getDescription()).thenReturn("description");
    when(mockGood.getQuantity()).thenReturn(2);
    when(mockGood.getPriceNQT()).thenReturn(3L);
    when(mockGood.getSellerId()).thenReturn(4L);
    when(mockGood.getTags()).thenReturn("tags");
    when(mockGood.isDelisted()).thenReturn(true);
    when(mockGood.getTimestamp()).thenReturn(5);

    return mockGood;
  }
}
