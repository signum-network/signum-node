package brs.services.impl.chainscan;

import brs.Block;
import signumj.crypto.SignumCrypto;

import java.util.Arrays;
import java.util.List;

/**
 * {@code GENERATION_SIGNATURE}: the generation signature follows from the previous block's generation
 * signature and generator, as in {@code BlockServiceImpl.verifyGenerationSignature}.
 */
public class GenerationSignatureCheck implements ChainCheck {

    public static final String GENERATION_SIGNATURE = "GENERATION_SIGNATURE";

    private final SignumCrypto signumCrypto;

    public GenerationSignatureCheck(SignumCrypto signumCrypto) {
        this.signumCrypto = signumCrypto;
    }

    @Override
    public String group() {
        return "block";
    }

    @Override
    public List<Finding> check(BlockContext context) {
        ScannedBlock stored = context.block();
        ScannedBlock previous = context.previous();
        if (stored.block() == null || previous == null || previous.block() == null) {
            return List.of(); // BLOCK_LOAD and CHAIN_LINK report it
        }
        Block block = stored.block();
        Block previousBlock = previous.block();
        try {
            byte[] expected = signumCrypto.calculateGenerationSignature(previousBlock.getGenerationSignature(),
                    previousBlock.getGeneratorId());
            if (!Arrays.equals(expected, block.getGenerationSignature())) {
                return List.of(Finding.ofBlock(GENERATION_SIGNATURE, Severity.ERROR, stored,
                        "generation signature doesn't follow from the previous block"));
            }
            return List.of();
        } catch (RuntimeException e) {
            return List.of(Finding.ofBlock(GENERATION_SIGNATURE, Severity.ERROR, stored,
                    "cannot calculate the generation signature: " + e));
        }
    }
}
