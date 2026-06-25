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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class TransactionTypeDistributeToHoldersTest {

    private static final long ASSET_ID = 100L;

    private AssetExchange mockAssetExchange;
    private Asset mockAsset;
    private FluxCapacitor mockFluxCapacitor;

    @Before
    public void setUp() {
        mockAssetExchange = mock(AssetExchange.class);
        mockAsset = mock(Asset.class);
        mockFluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.SMART_TOKEN);

        when(mockAssetExchange.getAsset(ASSET_ID)).thenReturn(mockAsset);
        when(mockAssetExchange.getAssetCirculatingSupply(eq(mockAsset), anyBoolean(), anyBoolean())).thenReturn(1000L);

        TransactionType.init(
            mock(Blockchain.class),
            mockFluxCapacitor,
            mock(AccountService.class),
            mock(DGSGoodsStoreService.class),
            mock(AliasService.class),
            mockAssetExchange,
            mock(SubscriptionService.class),
            mock(EscrowService.class)
        );
    }

    @Test(expected = SignumException.NotValidException.class)
    public void validateAttachment_givenNegativeQuantityQnt_throwsNotValidException() throws SignumException.ValidationException {
        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(mockFluxCapacitor);

            Attachment.ColoredCoinsAssetDistributeToHolders attachment =
                new Attachment.ColoredCoinsAssetDistributeToHolders(ASSET_ID, 0L, 0L, -100L, 0);

            Transaction mockTransaction = mock(Transaction.class);
            when(mockTransaction.getAttachment()).thenReturn(attachment);
            when(mockTransaction.getAmountNqt()).thenReturn(0L);

            TransactionType.ColoredCoins.ASSET_DISTRIBUTE_TO_HOLDERS.validateAttachment(mockTransaction);
        }
    }
}
