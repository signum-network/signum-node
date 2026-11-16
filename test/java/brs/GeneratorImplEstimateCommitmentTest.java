package brs;

import brs.common.QuickMocker;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratorImplEstimateCommitmentTest {

    private static final long GENERATOR_ID = 42L;
    private static final int PREVIOUS_HEIGHT = 1_000;
    private static final long COMMITTED_NQT = 8_000_000_000_000L;

    @Test
    void estimateCommitment_GivenMinimumCapacityChangingAboveThePreviousBlock_UsesTheValueAtThePreviousBlock() {
        // Arrange: the chain is already further than the previous block, where the minimum capacity differs
        Blockchain blockchain = mock(Blockchain.class);
        when(blockchain.getHeight()).thenReturn(PREVIOUS_HEIGHT + 50);
        when(blockchain.getCommittedAmount(eq(GENERATOR_ID), eq(PREVIOUS_HEIGHT), eq(PREVIOUS_HEIGHT), isNull()))
                .thenReturn(COMMITTED_NQT);
        when(blockchain.getBlocksCount(eq(GENERATOR_ID), anyInt(), anyInt())).thenReturn(5);

        FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.SPEEDWAY);
        doReturn(0).when(fluxCapacitor).getValue(eq(FluxValues.COMMITMENT_WAIT), anyInt());
        doReturn(8_000L).when(fluxCapacitor).getValue(FluxValues.MIN_CAPACITY, PREVIOUS_HEIGHT);
        doReturn(1_000L).when(fluxCapacitor).getValue(FluxValues.MIN_CAPACITY);

        Block previousBlock = mock(Block.class);
        when(previousBlock.getHeight()).thenReturn(PREVIOUS_HEIGHT);
        // a huge base target makes the estimated capacity fall below the minimum
        when(previousBlock.getCapacityBaseTarget()).thenReturn(Long.MAX_VALUE);

        GeneratorImpl generator = new GeneratorImpl(blockchain, null, null, null, fluxCapacitor);

        // Act
        long commitment = generator.estimateCommitment(GENERATOR_ID, previousBlock);

        // Assert: committed NQT per TiB at the minimum capacity of 8000 GiB
        assertEquals(COMMITTED_NQT / 8_000L * 1000L, commitment);
    }
}
