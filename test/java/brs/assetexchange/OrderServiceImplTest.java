package brs.assetexchange;

import brs.Order.Ask;
import brs.Order.Bid;
import brs.db.BurstKey;
import brs.db.BurstKey.LongKeyFactory;
import brs.db.VersionedEntityTable;
import brs.db.store.OrderStore;
import brs.services.AccountService;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OrderServiceImplTest {

  private OrderServiceImpl t;

  private OrderStore orderStoreMock;
  private VersionedEntityTable<Ask> mockAskOrderTable;
  private LongKeyFactory<Ask> mockAskOrderDbKeyFactory;
  private VersionedEntityTable<Bid> mockBidOrderTable;
  private LongKeyFactory<Bid> mockBidOrderDbKeyFactory;

  private AccountService accountServiceMock;
  private TradeServiceImpl tradeServiceMock;

  @Before
  public void setUp() {
    orderStoreMock = mock(OrderStore.class);
    mockAskOrderTable = mock(VersionedEntityTable.class);
    mockAskOrderDbKeyFactory = mock(LongKeyFactory.class);
    mockBidOrderTable = mock(VersionedEntityTable.class);
    mockBidOrderDbKeyFactory = mock(LongKeyFactory.class);

    accountServiceMock = mock(AccountService.class);
    tradeServiceMock = mock(TradeServiceImpl.class);

    when(orderStoreMock.getAskOrderTable()).thenReturn(mockAskOrderTable);
    when(orderStoreMock.getAskOrderDbKeyFactory()).thenReturn(mockAskOrderDbKeyFactory);
    when(orderStoreMock.getBidOrderTable()).thenReturn(mockBidOrderTable);
    when(orderStoreMock.getBidOrderDbKeyFactory()).thenReturn(mockBidOrderDbKeyFactory);

    t = new OrderServiceImpl(orderStoreMock, accountServiceMock, tradeServiceMock);
  }

  @Test
  public void getAskOrder() {
    final BurstKey mockAskKey = mock(BurstKey.class);
    final Ask mockAsk = mock(Ask.class);

    final long askKey = 123l;

    when(mockAskOrderDbKeyFactory.newKey(eq(askKey))).thenReturn(mockAskKey);
    when(mockAskOrderTable.get(eq(mockAskKey))).thenReturn(mockAsk);

    assertEquals(mockAsk, t.getAskOrder(askKey));
  }

  @Test
  public void getBidOrder() {
    final BurstKey mockBidKey = mock(BurstKey.class);
    final Bid mockBid = mock(Bid.class);

    final long bidKey = 123l;

    when(mockBidOrderDbKeyFactory.newKey(eq(bidKey))).thenReturn(mockBidKey);
    when(mockBidOrderTable.get(eq(mockBidKey))).thenReturn(mockBid);

    assertEquals(mockBid, t.getBidOrder(bidKey));
  }

  @Test
  public void getAllAskOrders() {
    final int from = 1;
    final int to = 5;

    final Collection<Ask> mockAskIterator = mock(Collection.class);

    when(mockAskOrderTable.getAll(eq(from), eq(to))).thenReturn(mockAskIterator);

    assertEquals(mockAskIterator, t.getAllAskOrders(from, to));
  }

  @Test
  public void getAllBidOrders() {
    final int from = 1;
    final int to = 5;

    final Collection<Bid> mockBidIterator = mock(Collection.class);

    when(mockBidOrderTable.getAll(eq(from), eq(to))).thenReturn(mockBidIterator);

    assertEquals(mockBidIterator, t.getAllBidOrders(from, to));
  }

  @Test
  public void getSortedBidOrders() {
    final long assetId = 123l;
    final int from = 1;
    final int to = 5;

    final Collection<Bid> mockBidIterator = mock(Collection.class);

    when(orderStoreMock.getSortedBids(eq(assetId), eq(from), eq(to))).thenReturn(mockBidIterator);

    assertEquals(mockBidIterator, t.getSortedBidOrders(assetId, from, to));
  }

  @Test
  public void getAskOrdersByAccount() {
    final long accountId = 123l;
    final int from = 1;
    final int to = 5;

    final Collection<Ask> mockAskIterator = mock(Collection.class);

    when(orderStoreMock.getAskOrdersByAccount(eq(accountId), eq(from), eq(to))).thenReturn(mockAskIterator);

    assertEquals(mockAskIterator, t.getAskOrdersByAccount(accountId, from, to));
  }

  @Test
  public void getAskOrdersByAccountAsset() {
    final long accountId = 123l;
    final long assetId = 456l;
    final int from = 1;
    final int to = 5;

    final Collection<Ask> mockAskIterator = mock(Collection.class);

    when(orderStoreMock.getAskOrdersByAccountAsset(eq(accountId), eq(assetId), eq(from), eq(to))).thenReturn(mockAskIterator);

    assertEquals(mockAskIterator, t.getAskOrdersByAccountAsset(accountId, assetId, from, to));
  }

  @Test
  public void getSortedAskOrders() {
    final long assetId = 456l;
    final int from = 1;
    final int to = 5;

    final Collection<Ask> mockAskIterator = mock(Collection.class);

    when(orderStoreMock.getSortedAsks(eq(assetId), eq(from), eq(to))).thenReturn(mockAskIterator);

    assertEquals(mockAskIterator, t.getSortedAskOrders(assetId, from, to));
  }

  @Test
  public void getBidCount() {
    when(mockBidOrderTable.getCount()).thenReturn(5);

    assertEquals(5, t.getBidCount());
  }

  @Test
  public void getAskCount() {
    when(mockAskOrderTable.getCount()).thenReturn(5);

    assertEquals(5, t.getAskCount());
  }

  @Test
  public void getBidOrdersByAccount() {
    final long accountId = 456l;
    final int from = 1;
    final int to = 5;

    final Collection<Bid> mockBidIterator = mock(Collection.class);

    when(orderStoreMock.getBidOrdersByAccount(eq(accountId), eq(from), eq(to))).thenReturn(mockBidIterator);

    assertEquals(mockBidIterator, t.getBidOrdersByAccount(accountId, from, to));
  }

  @Test
  public void getBidOrdersByAccountAsset() {
    final long accountId = 123L;
    final long assetId = 456l;
    final int from = 1;
    final int to = 5;

    final Collection<Bid> mockBidIterator = mock(Collection.class);

    when(orderStoreMock.getBidOrdersByAccountAsset(eq(accountId), eq(assetId), eq(from), eq(to))).thenReturn(mockBidIterator);

    assertEquals(mockBidIterator, t.getBidOrdersByAccountAsset(accountId, assetId, from, to));
  }
}
