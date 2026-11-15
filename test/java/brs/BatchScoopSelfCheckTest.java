package brs;

import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.TimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchScoopSelfCheckTest {

    private FluxCapacitor fluxCapacitor;
    private GeneratorImpl realGenerator;

    @BeforeEach
    void setUp() {
        fluxCapacitor = mock(FluxCapacitor.class);
        when(fluxCapacitor.getValue(eq(FluxValues.POC2), anyInt()))
                .thenAnswer(invocation -> (int) invocation.getArgument(1) >= 1000);
        realGenerator = new GeneratorImpl(mock(Blockchain.class), null, null, mock(TimeService.class), fluxCapacitor);
    }

    @Test
    void matchesJavaPlots_GivenNativeBatchCalculation_ReturnsTrue() {
        assertTrue(BatchScoopSelfCheck.matchesJavaPlots(realGenerator, fluxCapacitor));
    }

    @Test
    void matchesJavaPlots_GivenOneWrongScoopByte_ReturnsFalse() {
        // Arrange: correct scoops except one byte in the last block of the batch
        Generator generator = mock(Generator.class);
        when(generator.calculateScoops(any(), any(), any(), any())).thenAnswer(invocation -> {
            byte[] scoops = realGenerator.calculateScoops(invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(3));
            scoops[scoops.length - 1] ^= 1;
            return scoops;
        });

        // Act & Assert
        assertFalse(BatchScoopSelfCheck.matchesJavaPlots(generator, fluxCapacitor));
    }

    @Test
    void matchesJavaPlots_GivenCalculationFails_ReturnsFalse() {
        Generator generator = mock(Generator.class);
        when(generator.calculateScoops(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("create_scoops failed with status -1"));

        assertFalse(BatchScoopSelfCheck.matchesJavaPlots(generator, fluxCapacitor));
    }
}
