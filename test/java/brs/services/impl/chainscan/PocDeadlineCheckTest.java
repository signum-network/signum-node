package brs.services.impl.chainscan;

import brs.Block;
import brs.Generator;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static brs.services.impl.chainscan.ChainScanFixtures.block;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PocDeadlineCheckTest extends BlockChecksTestBase {

    private static final int PREVIOUS_HEIGHT = 9;
    private static final int PREVIOUS_TIMESTAMP = 1000;

    private final Generator generator = mock(Generator.class);
    private final FluxCapacitor fluxCapacitor = mock(FluxCapacitor.class);
    private final List<Integer> requestedHeights = new ArrayList<>();

    @BeforeEach
    void setUpGenerator() {
        when(fluxCapacitor.getValue(eq(FluxValues.POC_PLUS), anyInt())).thenReturn(true);
        when(generator.calculateScoop(any(), anyLong())).thenReturn(7);
        when(generator.calculateHit(anyLong(), anyLong(), any(), anyInt(), anyInt())).thenReturn(BigInteger.TEN);
        when(generator.estimateCommitment(anyLong(), any())).thenReturn(500L);
    }

    private PocDeadlineCheck check(int checkPointHeight) {
        return new PocDeadlineCheck(fluxCapacitor, height -> {
            requestedHeights.add(height);
            return generator;
        }, checkPointHeight);
    }

    private static BlockContext contextWithElapsed(int elapsed) throws Exception {
        Block previous = block(List.of(), ChainScanFixtures.payloadHash(List.of()), 0, 0L, 0L, 0L, null,
                PREVIOUS_HEIGHT, PREVIOUS_TIMESTAMP, new byte[32], new byte[32]);
        Block block = block(List.of(), ChainScanFixtures.payloadHash(List.of()), 0, 0L, 0L, 0L, null,
                PREVIOUS_HEIGHT + 1, PREVIOUS_TIMESTAMP + elapsed, new byte[32], new byte[32]);
        return new BlockContext(ChainScanFixtures.scanned(block, previous.getId(), 0L), List.of(),
                ChainScanFixtures.scanned(previous, 1L, block.getId()));
    }

    private void deadlineIs(long deadline) {
        when(generator.calculateDeadline(eq(BigInteger.TEN), anyLong(), eq(500L), anyLong(), eq(PREVIOUS_HEIGHT + 1)))
                .thenReturn(BigInteger.valueOf(deadline));
    }

    @Test
    void check_GivenElapsedTimeAboveDeadline_ReportsNothing() throws Exception {
        // Arrange
        deadlineIs(200);

        // Act + Assert
        assertTrue(check(-1).check(contextWithElapsed(201)).isEmpty());
    }

    @Test
    void check_GivenElapsedTimeBelowDeadline_ReportsError() throws Exception {
        // Arrange
        deadlineIs(700);

        // Act
        List<Finding> findings = check(-1).check(contextWithElapsed(1));

        // Assert
        assertEquals(1, findings.size());
        assertEquals(PocDeadlineCheck.POC_DEADLINE, findings.get(0).checkId());
        assertEquals(Severity.ERROR, findings.get(0).severity());
        assertTrue(findings.get(0).detail().startsWith("forged 699 s too early"), findings.get(0).detail());
    }

    @Test
    void check_GivenElapsedTimeEqualToDeadline_ReportsError() throws Exception {
        // Arrange: the node requires the elapsed time to exceed the deadline
        deadlineIs(240);

        // Act + Assert
        assertEquals(Severity.ERROR, check(-1).check(contextWithElapsed(240)).get(0).severity());
    }

    @Test
    void check_GivenViolationBelowCheckPoint_ReportsInfo() throws Exception {
        // Arrange
        deadlineIs(700);

        // Act
        List<Finding> findings = check(PREVIOUS_HEIGHT + 2).check(contextWithElapsed(1));

        // Assert
        assertEquals(Severity.INFO, findings.get(0).severity());
    }

    @Test
    void check_GivenViolationAtCheckPoint_ReportsError() throws Exception {
        // Arrange
        deadlineIs(700);

        // Act + Assert
        assertEquals(Severity.ERROR, check(PREVIOUS_HEIGHT + 1).check(contextWithElapsed(1)).get(0).severity());
    }

    @Test
    void check_UsesTheGeneratorAtThePreviousHeight() throws Exception {
        // Arrange
        deadlineIs(200);
        BlockContext context = contextWithElapsed(201);
        Block block = context.block().block();

        // Act
        check(-1).check(context);

        // Assert
        assertEquals(List.of(PREVIOUS_HEIGHT), requestedHeights);
        verify(generator).calculateScoop(block.getGenerationSignature(), PREVIOUS_HEIGHT + 1);
        verify(generator).calculateHit(block.getGeneratorId(), block.getNonce(), block.getGenerationSignature(), 7,
                PREVIOUS_HEIGHT + 1);
        verify(generator).estimateCommitment(block.getGeneratorId(), context.previous().block());
    }

    @Test
    void check_GivenHeightBeforePocPlus_UsesNoCommitment() throws Exception {
        // Arrange
        when(fluxCapacitor.getValue(eq(FluxValues.POC_PLUS), anyInt())).thenReturn(false);
        when(generator.calculateDeadline(eq(BigInteger.TEN), anyLong(), eq(0L), anyLong(), anyInt()))
                .thenReturn(BigInteger.valueOf(200));

        // Act
        List<Finding> findings = check(-1).check(contextWithElapsed(201));

        // Assert
        assertTrue(findings.isEmpty());
        verify(generator, never()).estimateCommitment(anyLong(), any());
    }

    @Test
    void check_GivenCalculationFailure_ReportsError() throws Exception {
        // Arrange
        when(generator.calculateHit(anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("boom"));

        // Act
        List<Finding> findings = check(-1).check(contextWithElapsed(201));

        // Assert
        assertEquals(Severity.ERROR, findings.get(0).severity());
        assertTrue(findings.get(0).detail().startsWith("cannot calculate the deadline"));
    }

    @Test
    void check_GivenNoPreviousBlock_ReportsNothing() throws Exception {
        // Arrange: CHAIN_LINK reports the missing block
        BlockContext context = contextWithElapsed(1);

        // Act + Assert
        assertTrue(check(-1).check(new BlockContext(context.block(), List.of(), null)).isEmpty());
    }
}
