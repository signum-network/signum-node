package brs.services.impl.chainscan;

import brs.Block;
import brs.Generator;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;

import java.math.BigInteger;
import java.util.List;
import java.util.function.IntFunction;

/**
 * {@code POC_DEADLINE}: the time since the previous block exceeds the block's deadline, as in
 * {@code BlockServiceImpl.verifyGenerationSignature}. The hit, the generator's commitment and the
 * deadline are calculated with the node's {@link Generator}.
 *
 * <p>Below {@code node.checkPointHeight} the node uses a hit of zero and so never rejects a block for its
 * deadline; violations there are reported as {@code INFO}.
 */
public class PocDeadlineCheck implements ChainCheck {

    public static final String POC_DEADLINE = "POC_DEADLINE";

    private final FluxCapacitor fluxCapacitor;
    private final IntFunction<Generator> generatorAtHeight;
    private final int checkPointHeight;

    /**
     * @param generatorAtHeight returns a generator that sees the stored chain up to the given height, as a
     *                          node does when the block at that height is its last block
     */
    public PocDeadlineCheck(FluxCapacitor fluxCapacitor, IntFunction<Generator> generatorAtHeight,
            int checkPointHeight) {
        this.fluxCapacitor = fluxCapacitor;
        this.generatorAtHeight = generatorAtHeight;
        this.checkPointHeight = checkPointHeight;
    }

    @Override
    public String group() {
        return "poc";
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
        int height = block.getHeight();
        try {
            Generator generator = generatorAtHeight.apply(previousBlock.getHeight());
            int scoop = generator.calculateScoop(block.getGenerationSignature(), height);
            BigInteger hit = generator.calculateHit(block.getGeneratorId(), block.getNonce(),
                    block.getGenerationSignature(), scoop, height);
            long commitment = fluxCapacitor.getValue(FluxValues.POC_PLUS, height)
                    ? generator.estimateCommitment(block.getGeneratorId(), previousBlock)
                    : 0L;
            BigInteger deadline = generator.calculateDeadline(hit, previousBlock.getCapacityBaseTarget(), commitment,
                    previousBlock.getAverageCommitment(), height);
            int elapsed = block.getTimestamp() - previousBlock.getTimestamp();
            if (BigInteger.valueOf(elapsed).compareTo(deadline) > 0) {
                return List.of();
            }
            Severity severity = height < checkPointHeight ? Severity.INFO : Severity.ERROR;
            return List.of(Finding.ofBlock(POC_DEADLINE, severity, stored,
                    "forged " + deadline.subtract(BigInteger.valueOf(elapsed)) + " s too early: elapsed "
                            + elapsed + " s, deadline " + deadline + " s, commitment " + commitment
                            + " NQT/TiB, average commitment " + previousBlock.getAverageCommitment() + " NQT/TiB"));
        } catch (RuntimeException e) {
            return List.of(Finding.ofBlock(POC_DEADLINE, Severity.ERROR, stored,
                    "cannot calculate the deadline: " + e));
        }
    }
}
