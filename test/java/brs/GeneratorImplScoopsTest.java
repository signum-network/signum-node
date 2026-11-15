package brs;

import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.props.PropertyService;
import brs.services.TimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import signumj.crypto.SignumCrypto;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import brs.util.MiningPlot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GeneratorImpl#calculateScoops} with the real native library: against the signumj
 * implementation it delegates to, and against the scalar consensus paths it replaces (the Java plot
 * generation in {@link MiningPlot} and the single-nonce {@link GeneratorImpl#calculateHit}).
 */
class GeneratorImplScoopsTest {

    private static final int POC2_HEIGHT = 500;
    private static final int SCOOP_SIZE = 64;

    /** Around the SIMD lane counts: 4 (SSE2, NEON), 8 (AVX2), 16 (AVX-512). */
    private static final int[] BATCH_SIZES = {1, 3, 7, 8, 9, 16, 17};

    private FluxCapacitor fluxCapacitor;
    private GeneratorImpl generator;

    @BeforeEach
    void setUp() {
        fluxCapacitor = mock(FluxCapacitor.class);
        when(fluxCapacitor.getValue(eq(FluxValues.POC2), anyInt()))
                .thenAnswer(invocation -> (int) invocation.getArgument(1) >= POC2_HEIGHT);
        generator = new GeneratorImpl(mock(Blockchain.class), null, null, mock(TimeService.class), fluxCapacitor);
    }

    private static byte[] scoopOf(byte[] scoops, int index) {
        return Arrays.copyOfRange(scoops, index * SCOOP_SIZE, (index + 1) * SCOOP_SIZE);
    }

    @Test
    void calculateScoops_GivenPoc2Blocks_MatchesSignumjBatchScoops() {
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
    void calculateScoops_GivenBatchSizesAroundSimdLaneCounts_MatchesJavaPlotGeneration() {
        for (int height : new int[] {POC2_HEIGHT - 1, POC2_HEIGHT}) {
            for (int count : BATCH_SIZES) {
                // Arrange
                Batch batch = batch(count, height, count * 31L + height);

                // Act
                byte[] result = generator.calculateScoops(batch.accountIds, batch.nonces, batch.scoops, batch.heights);

                // Assert: each scoop equals the one cut from the full Java-generated plot
                byte[][] expected = IntStream.range(0, count).parallel()
                        .mapToObj(i -> new MiningPlot(batch.accountIds[i], batch.nonces[i], height, fluxCapacitor)
                                .getScoop(batch.scoops[i]))
                        .toArray(byte[][]::new);
                for (int i = 0; i < count; i++) {
                    final int index = i;
                    assertArrayEquals(expected[i], scoopOf(result, i),
                            () -> "height " + height + ", batch of " + count + ", block " + index);
                }
            }
        }
    }

    @Test
    void calculateHit_GivenBatchedScoops_EqualsSingleNonceHit() {
        byte[] genSig = new byte[32];
        new Random(7).nextBytes(genSig);
        for (int count : BATCH_SIZES) {
            // Arrange: PoC1 and PoC2 blocks mixed in one batch
            Batch batch = batch(count, POC2_HEIGHT, count);
            for (int i = 0; i < count; i += 2) {
                batch.heights[i] = POC2_HEIGHT - 1;
            }

            // Act
            byte[] result = generator.calculateScoops(batch.accountIds, batch.nonces, batch.scoops, batch.heights);

            // Assert: the hit from the batched scoop equals the hit the node computed until now
            for (int i = 0; i < count; i++) {
                final int index = i;
                assertEquals(
                        generator.calculateHit(batch.accountIds[i], batch.nonces[i], genSig, batch.scoops[i], batch.heights[i]),
                        generator.calculateHit(genSig, scoopOf(result, i)),
                        () -> "batch of " + count + ", block " + index);
            }
        }
    }

    private record Batch(long[] accountIds, long[] nonces, int[] scoops, int[] heights) {
    }

    /** Random blocks plus edge values: min/negative/zero ids, max/zero nonce, first and last scoop. */
    private static Batch batch(int count, int height, long seed) {
        Random random = new Random(seed);
        long[] accountIds = new long[count];
        long[] nonces = new long[count];
        int[] scoops = new int[count];
        int[] heights = new int[count];
        for (int i = 0; i < count; i++) {
            accountIds[i] = random.nextLong();
            nonces[i] = random.nextLong();
            scoops[i] = random.nextInt(MiningPlot.SCOOPS_PER_PLOT);
            heights[i] = height;
        }
        accountIds[0] = Long.MIN_VALUE;
        nonces[0] = Long.MAX_VALUE;
        scoops[0] = 0;
        if (count > 1) {
            accountIds[count - 1] = count % 2 == 0 ? 0L : -1L;
            nonces[count - 1] = 0L;
            scoops[count - 1] = MiningPlot.SCOOPS_PER_PLOT - 1;
        }
        return new Batch(accountIds, nonces, scoops, heights);
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
