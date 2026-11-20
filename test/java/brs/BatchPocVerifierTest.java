package brs;

import brs.BlockchainProcessor.BlockNotAcceptedException;
import brs.services.BlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BatchPocVerifierTest {

    private static final int CHECKPOINT_HEIGHT = 1_000;
    private static final int SCOOP_SIZE = 64;

    private BlockService blockService;
    private Generator generator;
    private BatchPocVerifier subject;

    @BeforeEach
    void setUp() {
        blockService = mock(BlockService.class);
        generator = mock(Generator.class);
        subject = new BatchPocVerifier(blockService, generator, CHECKPOINT_HEIGHT);
    }

    /** A block at the given height whose previous block is the returned task's previous block. */
    private BatchPocVerifier.Task task(int height) {
        Block previous = mock(Block.class);
        when(previous.getId()).thenReturn(10_000L + height);
        Block block = mock(Block.class);
        when(block.getHeight()).thenReturn(height);
        when(block.getPreviousBlockId()).thenReturn(10_000L + height);
        when(block.getGeneratorId()).thenReturn(20_000L + height);
        when(block.getNonce()).thenReturn(30_000L + height);
        when(blockService.getScoopNum(block)).thenReturn(height % 4096);
        return new BatchPocVerifier.Task(block, previous);
    }

    /** Scoops filled with the entry index, so every block's slice is recognizable. */
    private static byte[] scoops(int count) {
        byte[] scoops = new byte[count * SCOOP_SIZE];
        for (int i = 0; i < count; i++) {
            Arrays.fill(scoops, i * SCOOP_SIZE, (i + 1) * SCOOP_SIZE, (byte) (i + 1));
        }
        return scoops;
    }

    private static byte[] scoop(int index) {
        byte[] scoop = new byte[SCOOP_SIZE];
        Arrays.fill(scoop, (byte) (index + 1));
        return scoop;
    }

    @Test
    void preVerify_GivenBlocksAboveCheckpoint_CalculatesScoopsInOneCallAndVerifiesEachWithItsScoop() throws Exception {
        // Arrange
        BatchPocVerifier.Task first = task(CHECKPOINT_HEIGHT);
        BatchPocVerifier.Task second = task(CHECKPOINT_HEIGHT + 1);
        when(generator.calculateScoops(any(), any(), any(), any())).thenReturn(scoops(2));

        // Act
        subject.preVerify(List.of(first, second));

        // Assert
        verify(generator).calculateScoops(
                aryEq(new long[] {20_000L + CHECKPOINT_HEIGHT, 20_000L + CHECKPOINT_HEIGHT + 1}),
                aryEq(new long[] {30_000L + CHECKPOINT_HEIGHT, 30_000L + CHECKPOINT_HEIGHT + 1}),
                aryEq(new int[] {CHECKPOINT_HEIGHT, CHECKPOINT_HEIGHT + 1}),
                aryEq(new int[] {CHECKPOINT_HEIGHT, CHECKPOINT_HEIGHT + 1}));
        InOrder inOrder = inOrder(blockService);
        inOrder.verify(blockService).preVerify(eq(first.block()), eq(first.previousBlock()), aryEq(scoop(0)));
        inOrder.verify(blockService).preVerify(eq(second.block()), eq(second.previousBlock()), aryEq(scoop(1)));
    }

    @Test
    void preVerify_GivenBlocksBelowCheckpoint_VerifiesWithoutScoops() throws Exception {
        // Arrange
        BatchPocVerifier.Task task = task(CHECKPOINT_HEIGHT - 1);

        // Act
        subject.preVerify(List.of(task));

        // Assert: below the checkpoint the PoC is not verified, so nothing to calculate
        verifyNoInteractions(generator);
        verify(blockService).preVerify(task.block(), task.previousBlock());
    }

    @Test
    void preVerify_GivenMixedBatch_KeepsOrderAndGivesScoopsOnlyToBlocksNeedingPoc() throws Exception {
        // Arrange
        BatchPocVerifier.Task belowCheckpoint = task(CHECKPOINT_HEIGHT - 1);
        BatchPocVerifier.Task aboveCheckpoint = task(CHECKPOINT_HEIGHT + 5);
        BatchPocVerifier.Task alreadyVerified = task(CHECKPOINT_HEIGHT + 6);
        when(alreadyVerified.block().isVerified()).thenReturn(true);
        BatchPocVerifier.Task aboveCheckpoint2 = task(CHECKPOINT_HEIGHT + 7);
        when(generator.calculateScoops(any(), any(), any(), any())).thenReturn(scoops(2));

        // Act
        subject.preVerify(List.of(belowCheckpoint, aboveCheckpoint, alreadyVerified, aboveCheckpoint2));

        // Assert
        verify(generator).calculateScoops(any(), any(), any(),
                aryEq(new int[] {CHECKPOINT_HEIGHT + 5, CHECKPOINT_HEIGHT + 7}));
        InOrder inOrder = inOrder(blockService);
        inOrder.verify(blockService).preVerify(belowCheckpoint.block(), belowCheckpoint.previousBlock());
        inOrder.verify(blockService).preVerify(eq(aboveCheckpoint.block()), eq(aboveCheckpoint.previousBlock()), aryEq(scoop(0)));
        inOrder.verify(blockService).preVerify(alreadyVerified.block(), alreadyVerified.previousBlock());
        inOrder.verify(blockService).preVerify(eq(aboveCheckpoint2.block()), eq(aboveCheckpoint2.previousBlock()), aryEq(scoop(1)));
    }

    @Test
    void preVerify_GivenPreviousBlockDoesNotMatch_CalculatesNoScoopForIt() throws Exception {
        // Arrange
        BatchPocVerifier.Task task = task(CHECKPOINT_HEIGHT + 1);
        when(task.block().getPreviousBlockId()).thenReturn(-1L);

        // Act
        subject.preVerify(List.of(task));

        // Assert: preVerify itself rejects the wrong previous block
        verifyNoInteractions(generator);
        verify(blockService).preVerify(task.block(), task.previousBlock());
    }

    @Test
    void preVerify_GivenScoopCalculationFails_VerifiesBlocksOneByOne() throws Exception {
        // Arrange
        BatchPocVerifier.Task first = task(CHECKPOINT_HEIGHT + 1);
        BatchPocVerifier.Task second = task(CHECKPOINT_HEIGHT + 2);
        when(generator.calculateScoops(any(), any(), any(), any())).thenThrow(new IllegalStateException("native failure"));

        // Act
        subject.preVerify(List.of(first, second));

        // Assert
        verify(blockService).preVerify(first.block(), first.previousBlock());
        verify(blockService).preVerify(second.block(), second.previousBlock());
        verify(blockService, never()).preVerify(any(), any(), any());
    }

    @Test
    void preVerify_GivenBlockFailsPreVerification_ContinuesWithNextBlocks() throws Exception {
        // Arrange
        BatchPocVerifier.Task invalid = task(CHECKPOINT_HEIGHT + 1);
        BatchPocVerifier.Task valid = task(CHECKPOINT_HEIGHT + 2);
        when(generator.calculateScoops(any(), any(), any(), any())).thenReturn(scoops(2));
        doThrow(new BlockNotAcceptedException("bad signature"))
                .when(blockService).preVerify(eq(invalid.block()), eq(invalid.previousBlock()), aryEq(scoop(0)));

        // Act
        subject.preVerify(List.of(invalid, valid));

        // Assert: the invalid block stays unverified, the importer rejects it
        verify(blockService).preVerify(eq(valid.block()), eq(valid.previousBlock()), aryEq(scoop(1)));
    }

    @Test
    void preVerify_GivenInterrupt_PropagatesAndStops() throws Exception {
        // Arrange
        BatchPocVerifier.Task first = task(CHECKPOINT_HEIGHT - 2);
        BatchPocVerifier.Task second = task(CHECKPOINT_HEIGHT - 1);
        doThrow(new InterruptedException()).when(blockService).preVerify(first.block(), first.previousBlock());

        // Act + Assert
        assertThrows(InterruptedException.class, () -> subject.preVerify(List.of(first, second)));
        verify(blockService, never()).preVerify(second.block(), second.previousBlock());
    }

    @Test
    void preVerify_GivenNoBlocks_DoesNothing() throws Exception {
        // Act
        subject.preVerify(Collections.emptyList());

        // Assert
        verifyNoInteractions(generator, blockService);
    }
}
