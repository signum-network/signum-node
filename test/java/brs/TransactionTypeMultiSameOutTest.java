package brs;

import brs.assetexchange.AssetExchange;
import brs.common.QuickMocker;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockedStatic;

import java.util.Arrays;

import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class TransactionTypeMultiSameOutTest {

    private static final int CURRENT_HEIGHT = 1_000_000;

    private FluxCapacitor mockFluxCapacitor;

    @Before
    public void setUp() {
        mockFluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.PRE_POC2);
        TransactionType.init(
            mock(Blockchain.class),
            mockFluxCapacitor,
            mock(AccountService.class),
            mock(DGSGoodsStoreService.class),
            mock(AliasService.class),
            mock(AssetExchange.class),
            mock(SubscriptionService.class),
            mock(EscrowService.class)
        );
    }

    /**
     * Consensus characterization: a multi-same-out whose amount is NOT evenly divisible by the
     * recipient count is, and must remain, valid. The per-recipient share is floor(amount/n), so
     * the indivisible remainder is burned (never minted). Tightening this into a rejection would
     * reject historically-accepted transactions on resync, so it is intentionally allowed.
     */
    @Test
    public void validateAttachment_givenAmountNotDivisibleByRecipients_passes() throws SignumException.ValidationException {
        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(mockFluxCapacitor);

            Attachment.PaymentMultiSameOutCreation attachment =
                new Attachment.PaymentMultiSameOutCreation(Arrays.asList(10L, 11L, 12L), CURRENT_HEIGHT);

            Transaction transaction = mock(Transaction.class);
            when(transaction.getAttachment()).thenReturn(attachment);
            when(transaction.getHeight()).thenReturn(CURRENT_HEIGHT);
            when(transaction.getAmountNqt()).thenReturn(100L); // 100 % 3 != 0

            TransactionType.Payment.MULTI_SAME_OUT.validateAttachment(transaction);
        }
    }
}
