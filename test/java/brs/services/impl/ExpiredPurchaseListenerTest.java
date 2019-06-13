package brs.services.impl;

import brs.Account;
import brs.Block;
import brs.DigitalGoodsStore.Purchase;
import brs.common.AbstractUnitTest;
import brs.services.AccountService;
import brs.services.DGSGoodsStoreService;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class ExpiredPurchaseListenerTest extends AbstractUnitTest {

  private AccountService accountServiceMock;
  private DGSGoodsStoreService dgsGoodsStoreServiceMock;

  private DGSGoodsStoreServiceImpl.ExpiredPurchaseListener t;

  @Before
  public void setUp() {
    accountServiceMock = mock(AccountService.class);
    dgsGoodsStoreServiceMock = mock(DGSGoodsStoreService.class);

    t = new DGSGoodsStoreServiceImpl.ExpiredPurchaseListener(accountServiceMock, dgsGoodsStoreServiceMock);
  }

  @Test
  public void notify_processesExpiredPurchases() {
    int blockTimestamp = 123;
    final Block block = mock(Block.class);
    when(block.getTimestamp()).thenReturn(blockTimestamp);

    long purchaseBuyerId = 34;
    final Account purchaseBuyer = mock(Account.class);
    when(purchaseBuyer.getId()).thenReturn(purchaseBuyerId);
    when(accountServiceMock.getAccount(eq(purchaseBuyer.getId()))).thenReturn(purchaseBuyer);

    final Purchase expiredPurchase = mock(Purchase.class);
    when(expiredPurchase.getQuantity()).thenReturn(5);
    when(expiredPurchase.getPriceNQT()).thenReturn(3000L);
    when(expiredPurchase.getBuyerId()).thenReturn(purchaseBuyerId);

    final Collection<Purchase> mockIterator = mockCollection(expiredPurchase);
    when(dgsGoodsStoreServiceMock.getExpiredPendingPurchases(eq(blockTimestamp))).thenReturn(mockIterator);

    t.notify(block);

    verify(accountServiceMock).addToUnconfirmedBalanceNQT(eq(purchaseBuyer), eq(15000L));

    verify(dgsGoodsStoreServiceMock).setPending(eq(expiredPurchase), eq(false));
  }
}