package brs.services.impl;

import brs.Blockchain;
import brs.Subscription;
import brs.common.AbstractUnitTest;
import brs.db.BurstKey;
import brs.db.BurstKey.LongKeyFactory;
import brs.db.TransactionDb;
import brs.db.VersionedEntityTable;
import brs.db.store.SubscriptionStore;
import brs.services.AccountService;
import brs.services.AliasService;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SubscriptionServiceImplTest extends AbstractUnitTest {

  private SubscriptionServiceImpl t;

  private SubscriptionStore mockSubscriptionStore;
  private VersionedEntityTable<Subscription> mockSubscriptionTable;
  private LongKeyFactory<Subscription> mockSubscriptionDbKeyFactory;
  private TransactionDb transactionDb;
  private Blockchain blockchain;
  private AliasService aliasService;
  private AccountService accountService;


  @Before
  public void setUp() {
    mockSubscriptionStore = mock(SubscriptionStore.class);
    mockSubscriptionTable = mock(VersionedEntityTable.class);
    mockSubscriptionDbKeyFactory = mock(LongKeyFactory.class);

    when(mockSubscriptionStore.getSubscriptionTable()).thenReturn(mockSubscriptionTable);
    when(mockSubscriptionStore.getSubscriptionDbKeyFactory()).thenReturn(mockSubscriptionDbKeyFactory);

    t = new SubscriptionServiceImpl(mockSubscriptionStore, transactionDb, blockchain, aliasService, accountService);
  }

  @Test
  public void getSubscription() {
    final long subscriptionId = 123L;

    final BurstKey mockSubscriptionKey = mock(BurstKey.class);

    final Subscription mockSubscription = mock(Subscription.class);

    when(mockSubscriptionDbKeyFactory.newKey(eq(subscriptionId))).thenReturn(mockSubscriptionKey);
    when(mockSubscriptionTable.get(eq(mockSubscriptionKey))).thenReturn(mockSubscription);

    assertEquals(mockSubscription, t.getSubscription(subscriptionId));
  }

  @Test
  public void getSubscriptionsByParticipant() {
    long accountId = 123L;

    Collection<Subscription> mockSubscriptionIterator = mockCollection();
    when(mockSubscriptionStore.getSubscriptionsByParticipant(eq(accountId))).thenReturn(mockSubscriptionIterator);

    assertEquals(mockSubscriptionIterator, t.getSubscriptionsByParticipant(accountId));
  }

  @Test
  public void getSubscriptionsToId() {
    long accountId = 123L;

    Collection<Subscription> mockSubscriptionIterator = mockCollection();
    when(mockSubscriptionStore.getSubscriptionsToId(eq(accountId))).thenReturn(mockSubscriptionIterator);

    assertEquals(mockSubscriptionIterator, t.getSubscriptionsToId(accountId));
  }
}
