package brs.services.impl.chainscan;

import brs.Block;
import org.junit.jupiter.api.Test;
import signumj.crypto.SignumCrypto;

import java.util.Arrays;
import java.util.List;

import static brs.services.impl.chainscan.ChainScanFixtures.block;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationSignatureCheckTest extends BlockChecksTestBase {

    private final SignumCrypto crypto = SignumCrypto.getInstance();
    private final GenerationSignatureCheck check = new GenerationSignatureCheck(crypto);

    private static Block blockWithGenerationSignature(int height, int timestamp, byte[] generationSignature)
            throws Exception {
        return block(List.of(), ChainScanFixtures.payloadHash(List.of()), 0, 0L, 0L, 0L, null, height, timestamp,
                generationSignature, new byte[32]);
    }

    private BlockContext contextWith(byte[] generationSignature) throws Exception {
        byte[] previousGenerationSignature = new byte[32];
        Arrays.fill(previousGenerationSignature, (byte) 3);
        Block previous = blockWithGenerationSignature(9, 1000, previousGenerationSignature);
        Block block = blockWithGenerationSignature(10, 1240, generationSignature);
        return new BlockContext(ChainScanFixtures.scanned(block, previous.getId(), 0L), List.of(),
                ChainScanFixtures.scanned(previous, 1L, block.getId()));
    }

    private byte[] expectedGenerationSignature() {
        byte[] previousGenerationSignature = new byte[32];
        Arrays.fill(previousGenerationSignature, (byte) 3);
        return crypto.calculateGenerationSignature(previousGenerationSignature,
                brs.Account.getId(brs.common.TestConstants.TEST_PUBLIC_KEY_BYTES));
    }

    @Test
    void check_GivenGenerationSignatureFollowingFromPrevious_ReportsNothing() throws Exception {
        assertTrue(check.check(contextWith(expectedGenerationSignature())).isEmpty());
    }

    @Test
    void check_GivenOtherGenerationSignature_ReportsIt() throws Exception {
        // Act
        List<Finding> findings = check.check(contextWith(new byte[32]));

        // Assert
        assertEquals(1, findings.size());
        assertEquals(GenerationSignatureCheck.GENERATION_SIGNATURE, findings.get(0).checkId());
        assertEquals(Severity.ERROR, findings.get(0).severity());
    }

    @Test
    void check_GivenNoPreviousBlock_ReportsNothing() throws Exception {
        // Arrange: CHAIN_LINK reports the missing block
        BlockContext context = contextWith(new byte[32]);

        // Act + Assert
        assertTrue(check.check(new BlockContext(context.block(), List.of(), null)).isEmpty());
    }
}
