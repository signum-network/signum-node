package brs;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brs.fluxcapacitor.FluxCapacitor;
import brs.util.MiningPlot;

/**
 * Checks once at startup that the batched scoop calculation ({@link Generator#calculateScoops}, SIMD
 * code in the native library) gives the same scoops as the plain Java plot generation
 * ({@link MiningPlot}) on this machine. Batched verification is only used if it does.
 * <p>
 * The batch sizes are not multiples of the SIMD lane counts (4 for SSE2/NEON, 8 for AVX2, 16 for
 * AVX-512), so both full lane groups and the remainder are exercised. The cost is fixed (20 nonces,
 * once at startup), so it cannot be triggered by peers.
 */
public final class BatchScoopSelfCheck {

    private static final Logger logger = LoggerFactory.getLogger(BatchScoopSelfCheck.class);

    /** One more than the widest SIMD lane count (AVX-512). */
    static final int BATCH_SIZE = 17;
    private static final int POC1_HEIGHT = 1;
    private static final int POC2_HEIGHT = Integer.MAX_VALUE / 2;

    private BatchScoopSelfCheck() {
    }

    /**
     * @return true if the batched scoops of a PoC1 and a PoC2 batch equal the Java reference, false if
     *     any scoop differs or the calculation fails
     */
    public static boolean matchesJavaPlots(Generator generator, FluxCapacitor fluxCapacitor) {
        try {
            return matches(generator, fluxCapacitor, POC2_HEIGHT, BATCH_SIZE)
                    && matches(generator, fluxCapacitor, POC1_HEIGHT, 3);
        } catch (RuntimeException e) {
            logger.warn("Batched scoop calculation failed during the self check", e);
            return false;
        }
    }

    private static boolean matches(Generator generator, FluxCapacitor fluxCapacitor, int height, int count) {
        long[] accountIds = new long[count];
        long[] nonces = new long[count];
        int[] scoops = new int[count];
        int[] heights = new int[count];
        for (int i = 0; i < count; i++) {
            // fixed, spread out values including edge cases: negative ids, max nonce, first and last scoop
            accountIds[i] = i == 0 ? Long.MIN_VALUE : -7_046_029_254_386_353_131L * (i + 1);
            nonces[i] = i == 1 ? Long.MAX_VALUE : 0x9E3779B97F4A7C15L * i;
            scoops[i] = i == 2 ? MiningPlot.SCOOPS_PER_PLOT - 1 : (i * 977) % MiningPlot.SCOOPS_PER_PLOT;
            heights[i] = height;
        }
        byte[] batch = generator.calculateScoops(accountIds, nonces, scoops, heights);
        // the Java plot generation takes ~100 ms per nonce, so compute the references in parallel
        byte[][] expected = IntStream.range(0, count).parallel()
                .mapToObj(i -> new MiningPlot(accountIds[i], nonces[i], height, fluxCapacitor).getScoop(scoops[i]))
                .toArray(byte[][]::new);
        for (int i = 0; i < count; i++) {
            byte[] actual = Arrays.copyOfRange(batch, i * MiningPlot.SCOOP_SIZE, (i + 1) * MiningPlot.SCOOP_SIZE);
            if (!Arrays.equals(expected[i], actual)) {
                logger.error("Batched scoop {} of {} at height {} differs from the Java plot generation", i, count, height);
                return false;
            }
        }
        return true;
    }
}
