package brs.unconfirmedtransactions;

import brs.*;
import brs.Attachment.MessagingAliasSell;
import brs.BurstException.NotCurrentlyValidException;
import brs.BurstException.ValidationException;
import brs.Transaction.Builder;
import brs.common.QuickMocker;
import brs.common.TestConstants;
import brs.db.BurstKey;
import brs.db.BurstKey.LongKeyFactory;
import brs.db.TransactionDb;
import brs.db.VersionedBatchEntityTable;
import brs.db.store.AccountStore;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.peer.Peer;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.TimeService;
import brs.services.impl.TimeServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;

import static brs.Attachment.ORDINARY_PAYMENT;
import static brs.Constants.FEE_QUANT;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Burst.class)
public class UnconfirmedTransactionStoreTest {

  private BlockchainImpl mockBlockChain;

  private AccountStore accountStoreMock;
  private VersionedBatchEntityTable<Account> accountTableMock;
  private LongKeyFactory<Account> accountBurstKeyFactoryMock;

  private TimeService timeService = new TimeServiceImpl();
  private UnconfirmedTransactionStore t;

  @Before
  public void setUp() {
    mockStatic(Burst.class);

    final PropertyService mockPropertyService = mock(PropertyService.class);
    when(mockPropertyService.getInt(eq(Props.DB_MAX_ROLLBACK))).thenReturn(1440);
    when(Burst.getPropertyService()).thenReturn(mockPropertyService);
    when(mockPropertyService.getInt(eq(Props.P2P_MAX_UNCONFIRMED_TRANSACTIONS))).thenReturn(8192);
    when(mockPropertyService.getInt(eq(Props.P2P_MAX_PERCENTAGE_UNCONFIRMED_TRANSACTIONS_FULL_HASH_REFERENCE))).thenReturn(5);
    when(mockPropertyService.getInt(eq(Props.P2P_MAX_UNCONFIRMED_TRANSACTIONS_RAW_SIZE_BYTES_TO_SEND))).thenReturn(175000);

    mockBlockChain = mock(BlockchainImpl.class);
    when(Burst.getBlockchain()).thenReturn(mockBlockChain);

    accountStoreMock = mock(AccountStore.class);
    accountTableMock = mock(VersionedBatchEntityTable.class);
    accountBurstKeyFactoryMock = mock(LongKeyFactory.class);
    TransactionDb transactionDbMock = mock(TransactionDb.class, Answers.RETURNS_DEFAULTS);
    when(accountStoreMock.getAccountTable()).thenReturn(accountTableMock);
    when(accountStoreMock.getAccountKeyFactory()).thenReturn(accountBurstKeyFactoryMock);

    final Account mockAccount = mock(Account.class);
    final BurstKey mockAccountKey = mock(BurstKey.class);
    when(accountBurstKeyFactoryMock.newKey(eq(123L))).thenReturn(mockAccountKey);
    when(accountTableMock.get(eq(mockAccountKey))).thenReturn(mockAccount);
    when(mockAccount.getUnconfirmedBalanceNQT()).thenReturn(Constants.MAX_BALANCE_NQT);

    FluxCapacitor mockFluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.PRE_DYMAXION, FluxValues.DIGITAL_GOODS_STORE);

    when(Burst.getFluxCapacitor()).thenReturn(mockFluxCapacitor);

    TransactionType.init(mockBlockChain, mockFluxCapacitor, null, null, null, null, null, null);

    t = new UnconfirmedTransactionStoreImpl(timeService, mockPropertyService, accountStoreMock, transactionDbMock);
  }

  @DisplayName("When we add Unconfirmed Transactions to the store, they can be retrieved")
  @Test
  public void transactionsCanGetRetrievedAfterAddingThemToStore() throws ValidationException {

    when(mockBlockChain.getHeight()).thenReturn(20);

    for (int i = 1; i <= 100; i++) {
      Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, i, FEE_QUANT * 100, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).build();
      transaction.sign(TestConstants.TEST_SECRET_PHRASE);
      t.put(transaction, null);
    }

    assertEquals(100, t.getAll().size());
    assertNotNull(t.get(1L));
  }


  @DisplayName("When a transaction got added by a peer, he won't get it reflected at him when getting unconfirmed transactions")
  @Test
  public void transactionsGivenByPeerWontGetReturnedToPeer() throws ValidationException {
    Peer mockPeer = mock(Peer.class);
    Peer otherMockPeer = mock(Peer.class);

    when(mockBlockChain.getHeight()).thenReturn(20);

    for (int i = 1; i <= 100; i++) {
      Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, i, FEE_QUANT * 100, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).build();
      transaction.sign(TestConstants.TEST_SECRET_PHRASE);
      t.put(transaction, mockPeer);
    }

    assertEquals(0, t.getAllFor(mockPeer).size());
    assertEquals(100, t.getAllFor(otherMockPeer).size());
  }

  @DisplayName("When a transactions got handed by a peer and we mark his fingerprints, he won't get it back a second time")
  @Test
  public void transactionsMarkedWithPeerFingerPrintsWontGetReturnedToPeer() throws ValidationException {
    Peer mockPeer = mock(Peer.class);

    when(mockBlockChain.getHeight()).thenReturn(20);

    for (int i = 1; i <= 100; i++) {
      Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, i, FEE_QUANT * 100, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).build();
      transaction.sign(TestConstants.TEST_SECRET_PHRASE);
      t.put(transaction, null);
    }

    List<Transaction> mockPeerObtainedTransactions = t.getAllFor(mockPeer);
    assertEquals(100, mockPeerObtainedTransactions.size());

    t.markFingerPrintsOf(mockPeer, mockPeerObtainedTransactions);
    assertEquals(0, t.getAllFor(mockPeer).size());
  }


  @DisplayName("When The amount of unconfirmed transactions exceeds max size, and adding another then the cache size stays the same")
  @Test
  public void numberOfUnconfirmedTransactionsOfSameSlotExceedsMaxSizeAddAnotherThenCacheSizeStaysMaxSize() throws ValidationException {

    when(mockBlockChain.getHeight()).thenReturn(20);

    for (int i = 1; i <= 8192; i++) {
      Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, i, FEE_QUANT * 100, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).build();
      transaction.sign(TestConstants.TEST_SECRET_PHRASE);
      t.put(transaction, null);
    }

    assertEquals(8192, t.getAll().size());
    assertNotNull(t.get(1L));

    final Transaction oneTransactionTooMany =
        new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 9999, FEE_QUANT * 100, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
            .id(8193L).senderId(123L).build();
    oneTransactionTooMany.sign(TestConstants.TEST_SECRET_PHRASE);
    t.put(oneTransactionTooMany, null);

    assertEquals(8192, t.getAll().size());
    assertNull(t.get(1L));
  }

  @DisplayName("When the amount of unconfirmed transactions exceeds max size, and adding another of a higher slot, the cache size stays the same, and a lower slot transaction gets removed")
  @Test
  public void numberOfUnconfirmedTransactionsOfSameSlotExceedsMaxSizeAddAnotherThenCacheSizeStaysMaxSizeAndLowerSlotTransactionGetsRemoved() throws ValidationException {

    when(mockBlockChain.getHeight()).thenReturn(20);

    for (int i = 1; i <= 8192; i++) {
      Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, i, FEE_QUANT * 100, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).build();
      transaction.sign(TestConstants.TEST_SECRET_PHRASE);
      t.put(transaction, null);
    }

    assertEquals(8192, t.getAll().size());
    assertEquals(8192, t.getAll().stream().filter(t -> t.getFeeNQT() == FEE_QUANT * 100).count());
    assertNotNull(t.get(1L));

    final Transaction oneTransactionTooMany =
        new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 9999, FEE_QUANT * 200, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
            .id(8193L).senderId(123L).build();
    oneTransactionTooMany.sign(TestConstants.TEST_SECRET_PHRASE);
    t.put(oneTransactionTooMany, null);

    assertEquals(8192, t.getAll().size());
    assertEquals(8192 - 1, t.getAll().stream().filter(t -> t.getFeeNQT() == FEE_QUANT * 100).count());
    assertEquals(1, t.getAll().stream().filter(t -> t.getFeeNQT() == FEE_QUANT * 200).count());
  }

  @DisplayName("The unconfirmed transaction gets denied in case the account is unknown")
  @Test(expected = NotCurrentlyValidException.class)
  public void unconfirmedTransactionGetsDeniedForUnknownAccount() throws ValidationException {
    when(mockBlockChain.getHeight()).thenReturn(20);

    Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 735000, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
        .id(1).senderId(124L).build();
    transaction.sign(TestConstants.TEST_SECRET_PHRASE);
    t.put(transaction, null);
  }

  @DisplayName("The unconfirmed transaction gets denied in case the account does not have enough unconfirmed balance")
  @Test(expected = NotCurrentlyValidException.class)
  public void unconfirmedTransactionGetsDeniedForNotEnoughUnconfirmedBalance() throws ValidationException {
    when(mockBlockChain.getHeight()).thenReturn(20);

    Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, Constants.MAX_BALANCE_NQT, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
        .id(1).senderId(123L).build();
    transaction.sign(TestConstants.TEST_SECRET_PHRASE);

    try {
      t.put(transaction, null);
    } catch (NotCurrentlyValidException ex) {
      assertTrue(t.getAll().isEmpty());
      throw ex;
    }
  }

  @DisplayName("When adding the same unconfirmed transaction, nothing changes")
  @Test
  public void addingNewUnconfirmedTransactionWithSameIDResultsInNothingChanging() throws ValidationException {
    when(mockBlockChain.getHeight()).thenReturn(20);

    Peer mockPeer = mock(Peer.class);

    when(mockPeer.getPeerAddress()).thenReturn("mockPeer");

    Builder transactionBuilder = new Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, Constants.MAX_BALANCE_NQT - 100000, timeService.getEpochTime() + 50000,
        (short) 500, ORDINARY_PAYMENT)
        .id(1).senderId(123L);

    Transaction transaction1 = transactionBuilder.build();
    transaction1.sign(TestConstants.TEST_SECRET_PHRASE);
    t.put(transaction1, mockPeer);

    Transaction transaction2 = transactionBuilder.build();
    transaction2.sign(TestConstants.TEST_SECRET_PHRASE);

    t.put(transaction2, mockPeer);

    assertEquals(1, t.getAll().size());
  }

  @DisplayName("When the maximum number of transactions with full hash reference is reached, following ones are ignored")
  @Test
  public void whenMaximumNumberOfTransactionsWithFullHashReferenceIsReachedFollowingOnesAreIgnored() throws ValidationException {

    when(mockBlockChain.getHeight()).thenReturn(20);

    for (int i = 1; i <= 414; i++) {
      Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, i, FEE_QUANT * 2, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).referencedTransactionFullHash("b33f").build();
      transaction.sign(TestConstants.TEST_SECRET_PHRASE);
      t.put(transaction, null);
    }

    assertEquals(409, t.getAll().size());
  }

  @DisplayName("When the maximum number of transactions for a slot size is reached, following ones are ignored")
  @Test
  public void whenMaximumNumberOfTransactionsForSlotSizeIsReachedFollowingOnesAreIgnored() throws ValidationException {

    when(mockBlockChain.getHeight()).thenReturn(20);

    for (int i = 1; i <= 365; i++) {
      Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, i, FEE_QUANT, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).build();
      transaction.sign(TestConstants.TEST_SECRET_PHRASE);
      t.put(transaction, null);
    }

    assertEquals(360, t.getAll().size());

    for (int i = 1; i <= 725; i++) {
      Transaction transaction = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, i, FEE_QUANT * 2, timeService.getEpochTime() + 50000, (short) 500, ORDINARY_PAYMENT)
          .id(i).senderId(123L).build();
      transaction.sign(TestConstants.TEST_SECRET_PHRASE);
      t.put(transaction, null);
    }

    assertEquals(1080, t.getAll().size());
  }

  @Test
  public void cheaperDuplicateTransactionGetsRemoved() throws ValidationException {
    Transaction cheap = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, FEE_QUANT, timeService.getEpochTime() + 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(1).senderId(123L).build();

    Transaction expensive = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, FEE_QUANT * 2, timeService.getEpochTime() + 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(2).senderId(123L).build();

    t.put(cheap, null);

    assertEquals(1, t.getAll().size());
    assertNotNull(t.get(cheap.getId()));

    t.put(expensive, null);

    assertEquals(1, t.getAll().size());
    assertNull(t.get(cheap.getId()));
    assertNotNull(t.get(expensive.getId()));
  }

  @Test
  public void cheaperDuplicateTransactionNeverGetsAdded() throws ValidationException {
    Transaction cheap = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, FEE_QUANT, timeService.getEpochTime() + 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(1).senderId(123L).build();

    Transaction expensive = new Transaction.Builder((byte) 1, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, FEE_QUANT * 2, timeService.getEpochTime() + 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(2).senderId(123L).build();

    t.put(expensive, null);

    assertEquals(1, t.getAll().size());
    assertNotNull(t.get(expensive.getId()));

    t.put(cheap, null);

    assertEquals(1, t.getAll().size());
    assertNull(t.get(cheap.getId()));
    assertNotNull(t.get(expensive.getId()));
  }

}
