package brs;

import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.*;
import brs.assetexchange.AssetExchange;
import brs.common.QuickMocker;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class TransactionTypeCommitmentTest {

    private static final long SENDER_ID = 123L;
    private static final int CURRENT_HEIGHT = 1_000_000;

    private Blockchain mockBlockchain;
    private AccountService mockAccountService;
    private FluxCapacitor mockFluxCapacitor;

    @Before
    public void setUp() {
        mockBlockchain = mock(Blockchain.class);
        mockAccountService = mock(AccountService.class);
        mockFluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.SIGNUM);

        Block lastBlock = mock(Block.class);
        when(lastBlock.getHeight()).thenReturn(CURRENT_HEIGHT);
        when(mockBlockchain.getLastBlock()).thenReturn(lastBlock);

        when(mockAccountService.getAccount(SENDER_ID)).thenReturn(mock(Account.class));

        TransactionType.init(
            mockBlockchain,
            mockFluxCapacitor,
            mockAccountService,
            mock(DGSGoodsStoreService.class),
            mock(AliasService.class),
            mock(AssetExchange.class),
            mock(SubscriptionService.class),
            mock(EscrowService.class)
        );
    }

    private Transaction transactionWith(Attachment attachment) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getAttachment()).thenReturn(attachment);
        when(transaction.getSenderId()).thenReturn(SENDER_ID);
        return transaction;
    }

    @Test(expected = SignumException.NotValidException.class)
    public void validateAttachment_givenNegativeCommitmentAddAmount_throwsNotValidException() throws SignumException.ValidationException {
        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(mockFluxCapacitor);

            Transaction transaction = transactionWith(new Attachment.CommitmentAdd(-100L, CURRENT_HEIGHT));
            TransactionType.SignaMining.COMMITMENT_ADD.validateAttachment(transaction);
        }
    }

    @Test(expected = SignumException.NotValidException.class)
    public void validateAttachment_givenNegativeCommitmentRemoveAmount_throwsNotValidException() throws SignumException.ValidationException {
        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(mockFluxCapacitor);

            Transaction transaction = transactionWith(new Attachment.CommitmentRemove(-100L, CURRENT_HEIGHT));
            TransactionType.SignaMining.COMMITMENT_REMOVE.validateAttachment(transaction);
        }
    }

    @Test
    public void validateAttachment_givenNonNegativeCommitmentAddAmount_passes() throws SignumException.ValidationException {
        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(mockFluxCapacitor);

            Transaction transaction = transactionWith(new Attachment.CommitmentAdd(0L, CURRENT_HEIGHT));
            TransactionType.SignaMining.COMMITMENT_ADD.validateAttachment(transaction);

            Transaction positive = transactionWith(new Attachment.CommitmentAdd(100L, CURRENT_HEIGHT));
            TransactionType.SignaMining.COMMITMENT_ADD.validateAttachment(positive);
        }
    }

    @Test
    public void validateAttachment_givenNonNegativeCommitmentRemoveAmount_passes() throws SignumException.ValidationException {
        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(mockFluxCapacitor);

            Transaction transaction = transactionWith(new Attachment.CommitmentRemove(100L, CURRENT_HEIGHT));
            TransactionType.SignaMining.COMMITMENT_REMOVE.validateAttachment(transaction);
        }
    }

    @Test(expected = SignumException.NotValidException.class)
    public void validateAttachment_givenCommitmentAddExceedingCap_throwsNotValidException() throws SignumException.ValidationException {
        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(mockFluxCapacitor);
            when(mockBlockchain.getHeight()).thenReturn(CURRENT_HEIGHT);
            when(mockBlockchain.getCommittedAmount(eq(SENDER_ID), anyInt(), anyInt(), isNull())).thenReturn(Constants.MAX_TOTAL_COMMITMENT_NQT);

            // already at the cap; one more SIGNA via raw/manipulated bytes must be rejected at validation
            Transaction transaction = transactionWith(new Attachment.CommitmentAdd(Constants.ONE_SIGNA, CURRENT_HEIGHT));
            TransactionType.SignaMining.COMMITMENT_ADD.validateAttachment(transaction);
        }
    }

    @Test
    public void applyAttachmentUnconfirmed_givenCommitmentExceedingCap_returnsFalse() {
        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            Blockchain bc = mock(Blockchain.class);
            when(bc.getHeight()).thenReturn(CURRENT_HEIGHT);
            when(bc.getCommittedAmount(eq(SENDER_ID), anyInt(), anyInt(), isNull())).thenReturn(Constants.MAX_TOTAL_COMMITMENT_NQT);
            signumMock.when(Signum::getBlockchain).thenReturn(bc);
            signumMock.when(Signum::getFluxCapacitor).thenReturn(mockFluxCapacitor);

            Account sender = mock(Account.class);
            when(sender.getId()).thenReturn(SENDER_ID);

            // one more SIGNA on top of the already-at-cap total must be refused
            Transaction transaction = transactionWith(new Attachment.CommitmentAdd(Constants.ONE_SIGNA, CURRENT_HEIGHT));
            boolean applied = TransactionType.SignaMining.COMMITMENT_ADD.applyAttachmentUnconfirmed(transaction, sender);

            assertFalse(applied);
            verify(mockAccountService, never()).addToUnconfirmedBalanceNQT(any(Account.class), anyLong());
        }
    }

    @Test
    public void applyAttachmentUnconfirmed_givenCommitmentWithinCap_returnsTrue() {
        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            Blockchain bc = mock(Blockchain.class);
            when(bc.getHeight()).thenReturn(CURRENT_HEIGHT);
            when(bc.getCommittedAmount(eq(SENDER_ID), anyInt(), anyInt(), isNull())).thenReturn(0L);
            signumMock.when(Signum::getBlockchain).thenReturn(bc);
            signumMock.when(Signum::getFluxCapacitor).thenReturn(mockFluxCapacitor);

            long amount = 100L * Constants.ONE_SIGNA;
            Account sender = mock(Account.class);
            when(sender.getId()).thenReturn(SENDER_ID);
            when(sender.getUnconfirmedBalanceNqt()).thenReturn(Long.MAX_VALUE);

            Transaction transaction = transactionWith(new Attachment.CommitmentAdd(amount, CURRENT_HEIGHT));
            boolean applied = TransactionType.SignaMining.COMMITMENT_ADD.applyAttachmentUnconfirmed(transaction, sender);

            assertTrue(applied);
            verify(mockAccountService).addToUnconfirmedBalanceNQT(sender, -amount);
        }
    }
}
