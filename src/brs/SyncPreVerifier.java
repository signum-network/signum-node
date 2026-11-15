package brs;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brs.BlockchainProcessor.BlockNotAcceptedException;
import brs.services.BlockService;
import brs.util.DownloadCacheImpl;

/**
 * Pre-verifies downloaded blocks during sync, outside of the block importer lock: in batches on the
 * CPU verifier threads, and the next block to import on the importer thread. A block that fails
 * stays unverified; the importer verifies it again under its lock and rejects it.
 */
class SyncPreVerifier {

    private static final Logger logger = LoggerFactory.getLogger(SyncPreVerifier.class);

    private final DownloadCacheImpl downloadCache;
    private final Blockchain blockchain;
    private final BlockService blockService;
    private final BatchPocVerifier batchPocVerifier;

    SyncPreVerifier(DownloadCacheImpl downloadCache, Blockchain blockchain, BlockService blockService,
            BatchPocVerifier batchPocVerifier) {
        this.downloadCache = downloadCache;
        this.blockchain = blockchain;
        this.blockService = blockService;
        this.batchPocVerifier = batchPocVerifier;
    }

    /**
     * Takes up to {@code batchSize} blocks from the unverified queue and pre-verifies them, computing
     * their PoC scoops in one call. Blocks whose previous block is neither in the download cache nor
     * in the chain no longer connect (e.g. after a pop off) and are skipped; the importer resets the
     * cache for them.
     */
    void preVerifyBatch(int batchSize) throws InterruptedException {
        List<BatchPocVerifier.Task> tasks = new ArrayList<>();
        for (Block block : downloadCache.takeUnverifiedBlocks(batchSize)) {
            Block previousBlock = downloadCache.getBlock(block.getPreviousBlockId());
            if (previousBlock == null) {
                previousBlock = blockchain.getBlock(block.getPreviousBlockId());
            }
            if (previousBlock != null) {
                tasks.add(new BatchPocVerifier.Task(block, previousBlock));
            }
        }
        batchPocVerifier.preVerify(tasks);
    }

    /**
     * Pre-verifies the block that follows the current tip, so the importer does not do it while
     * holding its write lock. Interrupts are kept on the thread; a failing block is left unverified.
     */
    void preVerifyNextBlock() {
        Block lastBlock = blockchain.getLastBlock();
        Block nextBlock = downloadCache.getNextBlock(lastBlock.getId());
        if (nextBlock == null || nextBlock.isVerified()) {
            return;
        }
        try {
            blockService.preVerify(nextBlock, lastBlock);
            if (nextBlock.isVerified()) {
                downloadCache.removeUnverified(nextBlock.getId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (BlockNotAcceptedException e) {
            logger.debug("Pre-verification of block {} failed, it is verified again on import",
                    nextBlock.getStringId(), e);
        }
    }
}
