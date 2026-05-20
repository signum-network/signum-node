package brs.services.impl;

import brs.Account;
import brs.Block;
import brs.Blockchain;
import brs.Generator;
import brs.Signum;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxEnable;
import brs.fluxcapacitor.FluxValues;
import brs.services.AccountService;
import brs.services.TransactionService;
import brs.util.DownloadCacheImpl;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BlockServiceImplTest extends AbstractUnitTest {

    private static Block mockBlock(long feeNqt, long cashBackNqt, long burntNqt) {
        Block block = mock(Block.class);
        when(block.getHeight()).thenReturn(100);
        when(block.getGeneratorId()).thenReturn(1L);
        when(block.getGeneratorPublicKey()).thenReturn(new byte[32]);
        when(block.getTotalFeeNqt()).thenReturn(feeNqt);
        when(block.getTotalFeeCashBackNqt()).thenReturn(cashBackNqt);
        when(block.getTotalFeeBurntNqt()).thenReturn(burntNqt);
        when(block.getTotalAmountNqt()).thenReturn(0L);
        when(block.getTransactions()).thenReturn(Collections.emptyList());
        return block;
    }

    private static BlockServiceImpl buildSubject() {
        Account generatorAccount = mock(Account.class);
        Account rewardAccount = mock(Account.class);
        Account nullAccount = mock(Account.class);
        when(rewardAccount.getPublicKey()).thenReturn(new byte[32]);

        AccountService accountService = mock(AccountService.class);
        when(accountService.getOrAddAccount(anyLong())).thenAnswer(inv ->
                ((long) inv.getArgument(0)) == 0L ? nullAccount : generatorAccount);
        when(accountService.getAccount(any(byte[].class))).thenReturn(rewardAccount);
        when(accountService.getRewardRecipientAssignment(any())).thenReturn(null);

        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getBlockReward(anyInt())).thenReturn(10_000_000_000L);

        return new BlockServiceImpl(accountService, mock(TransactionService.class),
                blockchain, mock(DownloadCacheImpl.class), mock(Generator.class), null);
    }

    private static FluxCapacitor smartFeesEnabled() {
        return QuickMocker.fluxCapacitorEnabledFunctionalities(
                (FluxEnable) FluxValues.REWARD_RECIPIENT_ENABLE,
                (FluxEnable) FluxValues.SMART_FEES);
    }

    /**
     * Reproduce the mainnet overflow: a block with negative totalFeeCashBackNqt
     * (as seen in block 1541011 with value -4611686018427387903) must be rejected.
     */
    @Test
    void apply_GivenNegativeFeeCashBack_ThrowsArithmeticException() {
        BlockServiceImpl subject = buildSubject();
        Block block = mockBlock(118_000_000L, -4_611_686_018_427_387_903L, 117_000_000L);
        FluxCapacitor flux = smartFeesEnabled();

        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(flux);
            assertThrows(ArithmeticException.class, () -> subject.apply(block),
                    "Negative fee cashback must be rejected to prevent inflated payout");
        }
    }

    @Test
    void apply_GivenNegativeFeeBurnt_ThrowsArithmeticException() {
        BlockServiceImpl subject = buildSubject();
        Block block = mockBlock(118_000_000L, 29_500_000L, -117_000_000L);
        FluxCapacitor flux = smartFeesEnabled();

        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(flux);
            assertThrows(ArithmeticException.class, () -> subject.apply(block),
                    "Negative fee burnt must be rejected");
        }
    }

    @Test
    void apply_GivenFeesExceedTotal_ThrowsArithmeticException() {
        BlockServiceImpl subject = buildSubject();
        // cashback + burnt > totalFee → rewardFees would go negative
        Block block = mockBlock(100_000_000L, 60_000_000L, 60_000_000L);
        FluxCapacitor flux = smartFeesEnabled();

        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(flux);
            assertThrows(ArithmeticException.class, () -> subject.apply(block),
                    "cashback + burnt exceeding totalFee must be rejected");
        }
    }

    @Test
    void apply_GivenValidSmartFees_DoesNotThrow() {
        BlockServiceImpl subject = buildSubject();
        // totalFee=118, cashback=29.5, burnt=88.5 → rewardFees=0
        Block block = mockBlock(118_000_000L, 29_500_000L, 88_500_000L);
        FluxCapacitor flux = smartFeesEnabled();

        try (MockedStatic<Signum> signumMock = mockStatic(Signum.class)) {
            signumMock.when(Signum::getFluxCapacitor).thenReturn(flux);
            assertDoesNotThrow(() -> subject.apply(block),
                    "Valid fee breakdown must be accepted");
        }
    }
}
