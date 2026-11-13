package brs.transactionduplicates;

import brs.Attachment.AdvancedPaymentEscrowResult;
import brs.Attachment.AdvancedPaymentSubscriptionSubscribe;
import brs.Attachment.MessagingAliasSell;
import brs.BlockchainImpl;
import brs.Signum;
import brs.Constants;
import brs.SignumException.NotValidException;
import brs.Escrow.DecisionType;
import brs.Transaction;
import brs.TransactionType;
import brs.common.QuickMocker;
import brs.common.TestConstants;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

public class TransactionDuplicatesCheckerImplTest {

  private MockedStatic<Signum> signumMock;

  private TransactionDuplicatesCheckerImpl t = new TransactionDuplicatesCheckerImpl();

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    final FluxCapacitor mockFluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.PRE_POC2);
    when(Signum.getFluxCapacitor()).thenReturn(mockFluxCapacitor);
    BlockchainImpl mockBlockchain = mock(BlockchainImpl.class);
    when(mockBlockchain.getHeight()).thenReturn(4);
    when(Signum.getBlockchain()).thenReturn(mockBlockchain);

    doReturn(Constants.FEE_QUANT_SIP3).when(mockFluxCapacitor).getValue(eq(FluxValues.FEE_QUANT), anyInt());

    TransactionType.init(mockBlockchain, mockFluxCapacitor, null, null, null, null, null, null);

    t = new TransactionDuplicatesCheckerImpl();
  }

  @DisplayName("First transaction is never a duplicate when checking for any duplicate")
  @Test
  public void firstTransactionIsNeverADuplicateWhenCheckingForAnyDuplicate() throws NotValidException {
    Transaction transaction = new Transaction.Builder((byte) 0, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 99999999, 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(1).senderId(123L).build();

    assertFalse(t.hasAnyDuplicate(transaction));
  }

  @DisplayName("Adding same transaction twice counts as a duplicate")
  @Test
  public void addingSameTransactionTwiceCountsAsADuplicate() throws NotValidException {
    Transaction transaction = new Transaction.Builder((byte) 0, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 99999999, 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(1).senderId(123L).build();

    assertFalse(t.hasAnyDuplicate(transaction));
    assertTrue(t.hasAnyDuplicate(transaction));
  }


  @DisplayName("Duplicate transaction is duplicate when checking for any duplicate")
  @Test
  public void duplicateTransactionIsDuplicateWhenCheckingForAnyDuplicate() throws NotValidException {
    Transaction transaction = new Transaction.Builder((byte) 0, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 99999999, 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(1).senderId(123L).build();

    Transaction duplicate = new Transaction.Builder((byte) 0, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 99999999, 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(2).senderId(345L).build();

    assertFalse(t.hasAnyDuplicate(transaction));
    assertTrue(t.hasAnyDuplicate(duplicate));
  }

  @DisplayName("Duplicate transaction removes cheaper duplicate when checking for cheapest duplicate")
  @Test
  public void duplicateTransactionRemovesCheaperDuplicateWhenCheckingForCheapestDuplicate() throws NotValidException {
    Transaction cheaper = new Transaction.Builder((byte) 0, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 999999, 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(1).senderId(123L).build();

    Transaction moreExpensive = new Transaction.Builder((byte) 0, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 999999999, 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(2).senderId(345L).build();

    final TransactionDuplicationResult hasCheaperFirst = t.removeCheaperDuplicate(cheaper);
    assertFalse(hasCheaperFirst.duplicate);
    assertNull(hasCheaperFirst.transaction);

    final TransactionDuplicationResult hasCheaperSecond = t.removeCheaperDuplicate(moreExpensive);
    assertTrue(hasCheaperSecond.duplicate);
    assertNotNull(hasCheaperSecond.transaction);
    assertEquals(cheaper, hasCheaperSecond.transaction);

    final TransactionDuplicationResult hasCheaperThird = t.removeCheaperDuplicate(cheaper);
    assertTrue(hasCheaperThird.duplicate);
    assertNotNull(hasCheaperThird.transaction);
    assertEquals(cheaper, hasCheaperThird.transaction);
  }

  @DisplayName("Some transactions are always a duplicate")
  @Test
  public void someTransactionsAreAlwaysADuplicate() throws NotValidException {
    Transaction transaction = new Transaction.Builder((byte) 0, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 99999999, 50000, (short) 500,
        new AdvancedPaymentEscrowResult(123L, DecisionType.REFUND,5))
        .id(1).senderId(123L).build();

    assertTrue(t.hasAnyDuplicate(transaction));
    assertTrue(t.removeCheaperDuplicate(transaction).duplicate);
  }


  @DisplayName("Some transaction are never a duplicate")
  @Test
  public void someTransactionsAreNeverADuplicate() throws NotValidException {
    Transaction transaction = new Transaction.Builder((byte) 0, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 99999999, 50000, (short) 500,
        new AdvancedPaymentSubscriptionSubscribe(123, 5))
        .id(1).senderId(123L).build();

    assertFalse(t.hasAnyDuplicate(transaction));
    assertFalse(t.hasAnyDuplicate(transaction));
    assertFalse(t.removeCheaperDuplicate(transaction).duplicate);
  }

  @DisplayName("Removing transaction makes it not a duplicate anymore")
  @Test
  public void removingTransactionMakesItNotADuplicateAnymore() throws NotValidException {
    Transaction transaction = new Transaction.Builder((byte) 0, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 99999999, 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(1).senderId(123L).build();

    Transaction duplicate = new Transaction.Builder((byte) 0, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 99999999, 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(2).senderId(345L).build();

    assertFalse(t.hasAnyDuplicate(transaction));
    assertTrue(t.hasAnyDuplicate(duplicate));

    t.removeTransaction(transaction);

    assertFalse(t.hasAnyDuplicate(duplicate));
  }

  @DisplayName("Clearing removes all transactions")
  @Test
  public void clearingRemovesAllTransactions() throws NotValidException {
    Transaction transaction = new Transaction.Builder((byte) 0, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 99999999, 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(1).senderId(123L).build();

    Transaction duplicate = new Transaction.Builder((byte) 0, TestConstants.TEST_PUBLIC_KEY_BYTES, 1, 99999999, 50000, (short) 500,
        new MessagingAliasSell("aliasName", 123, 5))
        .id(2).senderId(345L).build();

    assertFalse(t.hasAnyDuplicate(transaction));
    assertTrue(t.hasAnyDuplicate(duplicate));

    t.clear();

    assertFalse(t.hasAnyDuplicate(duplicate));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
