package brs;

import brs.assetexchange.AssetExchange;
import brs.common.QuickMocker;
import brs.fluxcapacitor.FluxCapacitor;
import brs.services.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class TransactionTypeEscrowTest {

    private static final long SENDER_ID = 1L;
    private static final long RECIPIENT_ID = 3L;
    private static final long SIGNER_ID = 2L;
    private static final int CURRENT_HEIGHT = 1_000_000;

    private EscrowService mockEscrowService;
    private FluxCapacitor mockFluxCapacitor;

    @Before
    public void setUp() {
        mockEscrowService = mock(EscrowService.class);
        when(mockEscrowService.isEnabled()).thenReturn(true);
        mockFluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities();

        TransactionType.init(
            mock(Blockchain.class),
            mockFluxCapacitor,
            mock(AccountService.class),
            mock(DGSGoodsStoreService.class),
            mock(AliasService.class),
            mock(AssetExchange.class),
            mock(SubscriptionService.class),
            mockEscrowService
        );
    }

    private Transaction transactionWith(Attachment attachment, long amountNqt) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getAttachment()).thenReturn(attachment);
        when(transaction.getSenderId()).thenReturn(SENDER_ID);
        when(transaction.getRecipientId()).thenReturn(RECIPIENT_ID);
        when(transaction.getAmountNqt()).thenReturn(amountNqt);
        when(transaction.getFeeNqt()).thenReturn(Constants.ONE_SIGNA);
        return transaction;
    }

    private Attachment.AdvancedPaymentEscrowCreation escrowCreation(long amountNqt) throws SignumException.NotValidException {
        return new Attachment.AdvancedPaymentEscrowCreation(
            amountNqt, 100, Escrow.DecisionType.REFUND, 1,
            Collections.singletonList(SIGNER_ID), CURRENT_HEIGHT);
    }

    @Test(expected = SignumException.NotValidException.class)
    public void validateAttachment_givenNegativeEscrowAmount_throwsNotValidException() throws SignumException.ValidationException {
        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(mockFluxCapacitor);

            Transaction transaction = transactionWith(escrowCreation(-100L), 0L);
            TransactionType.AdvancedPayment.ESCROW_CREATION.validateAttachment(transaction);
        }
    }

    @Test
    public void validateAttachment_givenNonNegativeEscrowAmount_passes() throws SignumException.ValidationException {
        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(mockFluxCapacitor);

            Transaction transaction = transactionWith(escrowCreation(100L), 0L);
            TransactionType.AdvancedPayment.ESCROW_CREATION.validateAttachment(transaction);
        }
    }
}
