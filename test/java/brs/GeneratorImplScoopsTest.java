package brs;

import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.props.PropertyService;
import brs.services.TimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import signumj.crypto.SignumCrypto;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GeneratorImpl#calculateScoops} against the signumj implementation it delegates to, with
 * the real native library.
 */
class GeneratorImplScoopsTest {

    private static final int POC2_HEIGHT = 500;
    private static final int SCOOP_SIZE = 64;

    private GeneratorImpl generator;

    @BeforeEach
    void setUp() {
        FluxCapacitor fluxCapacitor = mock(FluxCapacitor.class);
        when(fluxCapacitor.getValue(eq(FluxValues.POC2), anyInt()))
                .thenAnswer(invocation -> (int) invocation.getArgument(1) >= POC2_HEIGHT);
        generator = new GeneratorImpl(mock(Blockchain.class), null, null, mock(TimeService.class), fluxCapacitor);
    }

    private static byte[] scoopOf(byte[] scoops, int index) {
        return Arrays.copyOfRange(scoops, index * SCOOP_SIZE, (index + 1) * SCOOP_SIZE);
    }

    @Test
    void calculateScoops_GivenPoc2Blocks_MatchesSignumjPoc2Scoops() {
        // Arrange
        long[] accountIds = {6_502_115_112_683_865_257L, -42L, 1L};
        long[] nonces = {0L, 123_456_789L, Long.MAX_VALUE};
        int[] scoops = {0, 4095, 1234};
        int[] heights = {POC2_HEIGHT, POC2_HEIGHT + 1, POC2_HEIGHT + 100};

        // Act
        byte[] result = generator.calculateScoops(accountIds, nonces, scoops, heights);

        // Assert
        assertArrayEquals(SignumCrypto.getInstance().calculateScoops(accountIds, nonces, scoops, 2), result);
    }

    @Test
    void calculateScoops_GivenMixedPocVersions_UsesThePocVersionOfEachBlock() {
        // Arrange: same account, nonce and scoop, once before and once after the PoC2 fork
        long[] accountIds = {6_502_115_112_683_865_257L, 6_502_115_112_683_865_257L, 7L};
        long[] nonces = {12L, 12L, 99L};
        int[] scoops = {7, 7, 3000};
        int[] heights = {POC2_HEIGHT - 1, POC2_HEIGHT, POC2_HEIGHT - 10};
        SignumCrypto crypto = SignumCrypto.getInstance();

        // Act
        byte[] result = generator.calculateScoops(accountIds, nonces, scoops, heights);

        // Assert
        assertEquals(3 * SCOOP_SIZE, result.length);
        assertArrayEquals(crypto.calculateScoops(new long[] {accountIds[0]}, new long[] {12L}, new int[] {7}, 1), scoopOf(result, 0));
        assertArrayEquals(crypto.calculateScoops(new long[] {accountIds[1]}, new long[] {12L}, new int[] {7}, 2), scoopOf(result, 1));
        assertArrayEquals(crypto.calculateScoops(new long[] {7L}, new long[] {99L}, new int[] {3000}, 1), scoopOf(result, 2));
    }

    @Test
    void calculateScoops_GivenNoBlocks_ReturnsEmpty() {
        assertEquals(0, generator.calculateScoops(new long[0], new long[0], new int[0], new int[0]).length);
    }

    @Test
    void calculateScoops_GivenDifferentLengths_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> generator.calculateScoops(new long[] {1L}, new long[] {1L}, new int[] {0}, new int[0]));
    }

    @Test
    void calculateScoops_OfMockGenerator_ReturnsZeroScoopsWithoutCalculating() {
        // Arrange: the mock generator's hit ignores the scoop data
        GeneratorImpl.MockGenerator mockGenerator = new GeneratorImpl.MockGenerator(
                mock(PropertyService.class), mock(Blockchain.class), null, mock(TimeService.class), mock(FluxCapacitor.class));

        // Act
        byte[] result = mockGenerator.calculateScoops(new long[] {1L, 2L}, new long[] {1L, 2L}, new int[] {0, 1}, new int[] {1, 2});

        // Assert
        assertArrayEquals(new byte[2 * SCOOP_SIZE], result);
    }
}
