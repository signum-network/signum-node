package brs.assetexchange;

import brs.Trade;
import brs.common.AbstractUnitTest;
import brs.db.sql.EntitySqlTable;
import brs.db.store.TradeStore;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TradeServiceImplTest extends AbstractUnitTest {

  private TradeServiceImpl t;

  private TradeStore mockTradeStore;
  private EntitySqlTable<Trade> mockTradeTable;

  @Before
  public void setUp() {
    mockTradeStore = mock(TradeStore.class);
    mockTradeTable = mock(EntitySqlTable.class);

    when(mockTradeStore.getTradeTable()).thenReturn(mockTradeTable);

    t = new TradeServiceImpl(mockTradeStore);
  }

  @Test
  public void getAssetTrades() {
    final long assetId = 123l;
    final int from = 1;
    final int to = 5;

    final Collection<Trade> mockTradesIterator = mock(Collection.class);

    when(mockTradeStore.getAssetTrades(eq(assetId), eq(from), eq(to))).thenReturn(mockTradesIterator);

    assertEquals(mockTradesIterator, t.getAssetTrades(assetId, from, to));
  }

  @Test
  public void getAccountAssetTrades() {
    final long accountId = 12L;
    final long assetId = 123l;
    final int from = 1;
    final int to = 5;

    final Collection<Trade> mockAccountAssetTradesIterator = mock(Collection.class);

    when(mockTradeStore.getAccountAssetTrades(eq(accountId), eq(assetId), eq(from), eq(to))).thenReturn(mockAccountAssetTradesIterator);

    assertEquals(mockAccountAssetTradesIterator, t.getAccountAssetTrades(accountId, assetId, from, to));
  }

  @Test
  public void getAccountTrades() {
    final long accountId = 123l;
    final int from = 1;
    final int to = 5;

    final Collection<Trade> mockTradesIterator = mock(Collection.class);

    when(mockTradeStore.getAccountTrades(eq(accountId), eq(from), eq(to))).thenReturn(mockTradesIterator);

    assertEquals(mockTradesIterator, t.getAccountTrades(accountId, from, to));
  }

  @Test
  public void getCount() {
    final int count = 5;

    when(mockTradeTable.getCount()).thenReturn(count);

    assertEquals(count, t.getCount());
  }

  @Test
  public void getTradeCount() {
    final long assetId = 123L;
    final int count = 5;

    when(mockTradeStore.getTradeCount(eq(assetId))).thenReturn(count);

    assertEquals(count, t.getTradeCount(assetId));
  }

  @Test
  public void getAllTrades() {
    final int from = 1;
    final int to = 2;

    final Collection<Trade> mockTradeIterator = mockCollection();

    when(mockTradeTable.getAll(eq(from), eq(to))).thenReturn(mockTradeIterator);

    assertEquals(mockTradeIterator, t.getAllTrades(from, to));
  }
}
