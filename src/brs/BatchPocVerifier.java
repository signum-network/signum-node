package brs;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brs.BlockchainProcessor.BlockNotAcceptedException;
import brs.services.BlockService;
import brs.util.MiningPlot;

/**
 * Pre-verifies downloaded blocks in batches. The scoops of all blocks of a batch are computed in one
 * call, which uses SIMD instructions of the native library and is several times faster than one
 * block at a time. Each block is then pre-verified with its scoop.
 * <p>
 * A block that fails pre-verification stays unverified: the block importer verifies it again and
 * rejects it (blacklisting, pop off), as with single block verification.
 */
public class BatchPocVerifier {

    private static final Logger logger = LoggerFactory.getLogger(BatchPocVerifier.class);

    /** A downloaded block and the block before it (not null). */
    public record Task(Block block, Block previousBlock) {
    }

    private final BlockService blockService;
    private final Generator generator;
    private final int checkpointHeight;

    /**
     * @param checkpointHeight blocks below this height are not PoC verified, see
     *     {@link BlockService#preVerify(Block, Block, byte[])}
     */
    public BatchPocVerifier(BlockService blockService, Generator generator, int checkpointHeight) {
        this.blockService = blockService;
        this.generator = generator;
        this.checkpointHeight = checkpointHeight;
    }

    /**
     * Pre-verifies the blocks in the given order.
     *
     * @throws InterruptedException if the node is shutting down; the remaining blocks stay unverified
     */
    public void preVerify(List<Task> tasks) throws InterruptedException {
        List<Task> needingPoc = tasks.stream().filter(this::needsPocVerification).toList();
        byte[] scoops = calculateScoops(needingPoc);
        int pocIndex = 0;
        for (Task task : tasks) {
            byte[] scoop = null;
            if (scoops != null && pocIndex < needingPoc.size() && needingPoc.get(pocIndex) == task) {
                scoop = Arrays.copyOfRange(scoops, pocIndex * MiningPlot.SCOOP_SIZE, (pocIndex + 1) * MiningPlot.SCOOP_SIZE);
                pocIndex++;
            }
            preVerify(task, scoop);
        }
    }

    private boolean needsPocVerification(Task task) {
        Block block = task.block();
        return !block.isVerified()
                && block.getHeight() >= checkpointHeight
                && block.getPreviousBlockId() == task.previousBlock().getId();
    }

    /**
     * @return the scoops of the given blocks, or null if they could not be calculated in one batch
     */
    private byte[] calculateScoops(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return null;
        }
        int count = tasks.size();
        long[] accountIds = new long[count];
        long[] nonces = new long[count];
        int[] scoopNumbers = new int[count];
        int[] heights = new int[count];
        for (int i = 0; i < count; i++) {
            Block block = tasks.get(i).block();
            accountIds[i] = block.getGeneratorId();
            nonces[i] = block.getNonce();
            scoopNumbers[i] = blockService.getScoopNum(block);
            heights[i] = block.getHeight();
        }
        try {
            return generator.calculateScoops(accountIds, nonces, scoopNumbers, heights);
        } catch (RuntimeException e) {
            logger.warn("Batch scoop calculation for {} blocks failed, verifying them one by one", count, e);
            return null;
        }
    }

    private void preVerify(Task task, byte[] scoop) throws InterruptedException {
        try {
            if (scoop == null) {
                blockService.preVerify(task.block(), task.previousBlock());
            } else {
                blockService.preVerify(task.block(), task.previousBlock(), scoop);
            }
        } catch (BlockNotAcceptedException e) {
            logger.error("Block failed to preverify: ", e);
        }
    }
}
