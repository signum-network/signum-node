package brs;

import brs.BlockchainProcessor.BlockNotAcceptedException;
import brs.peer.Peer;
import brs.services.BlockService;
import brs.util.DownloadCacheImpl;
import brs.util.JSON;
import brs.util.ThreadPool;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import signumj.entity.SignumID;

import java.math.BigInteger;
import java.util.List;

/**
 * Switches the chain to a fork received from a peer, or keeps our own chain if the fork turns out
 * to be weaker.
 * <p>
 * Our blocks are popped off back to the common block and the fork blocks are pushed. If the
 * resulting chain has a lower cumulative difficulty than ours, the fork is popped off again and
 * our own blocks are restored. Transactions of blocks that end up off the chain go back to the
 * unconfirmed pool.
 * <p>
 * A peer is blacklisted only for faults of the peer (invalid or weaker fork), never because our
 * own database failed to persist a block (#1079).
 */
final class ForkProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ForkProcessor.class);
    private static final long CACHE_POLL_INTERVAL_MS = 1000;

    /** The chain operations fork processing needs, implemented by {@link BlockchainProcessorImpl}. */
    interface ChainOperations {

        /**
         * Pops blocks off the chain until {@code commonBlock} is the tip.
         *
         * @param forkBlocks fork blocks used to detect possible rewrite forks, may be {@code null}
         * @return the popped off blocks, newest first
         */
        List<Block> popOffTo(Block commonBlock, List<Block> forkBlocks);

        /**
         * Pushes a block on top of the chain.
         *
         * @throws BlockNotAcceptedException if the block is invalid
         * @throws BlockNotPersistedException if the block could not be persisted
         */
        void pushBlock(Block block) throws BlockNotAcceptedException;
    }

    private final Blockchain blockchain;
    private final BlockService blockService;
    private final DownloadCacheImpl downloadCache;
    private final TransactionProcessorImpl transactionProcessor;
    private final ChainOperations chain;

    ForkProcessor(Blockchain blockchain, BlockService blockService, DownloadCacheImpl downloadCache,
            TransactionProcessorImpl transactionProcessor, ChainOperations chain) {
        this.blockchain = blockchain;
        this.blockService = blockService;
        this.downloadCache = downloadCache;
        this.transactionProcessor = transactionProcessor;
        this.chain = chain;
    }

    /**
     * Waits until the download cache is processed, then switches to the fork starting after
     * {@code forkBlockId} if it is stronger than our chain. Fork processing is skipped if the
     * wait is interrupted, the node is shutting down or the common block is no longer in our
     * chain. The fork blocks and the download cache are reset afterwards, also on unexpected
     * errors.
     */
    void process(Peer peer, List<Block> forkBlocks, long forkBlockId) {
        logger.debug("A fork is detected. Waiting for cache to be processed.");
        downloadCache.lockCache(); // dont let anything add to cache!
        boolean cacheProcessed = awaitEmptyCache();
        synchronized (downloadCache) {
            synchronized (transactionProcessor.getUnconfirmedTransactionsSyncObj()) {
                try {
                    if (!cacheProcessed) {
                        logger.info("Skipping fork processing: interrupted or shutting down");
                        return;
                    }
                    Block forkBlock = blockchain.getBlock(forkBlockId);
                    if (forkBlock == null) {
                        logger.warn("Skipping fork processing: common block {} is no longer in our chain",
                                Long.toUnsignedString(forkBlockId));
                        return;
                    }
                    logger.debug("Cache is now processed. Starting to process fork.");
                    BigInteger curCumulativeDifficulty = blockchain.getLastBlock().getCumulativeDifficulty();
                    switchToFork(peer, forkBlocks, forkBlockId, forkBlock, curCumulativeDifficulty);
                    logger.info("Forkprocessing complete.");
                } finally {
                    // also on unexpected errors, so neither the fork nor a stale cache is kept around
                    downloadCache.resetForkBlocks();
                    downloadCache.resetCache(); // Reset and set cached vars to chaindata.
                }
            }
        }
    }

    /**
     * Waits until the download cache is empty.
     *
     * @return {@code false} if the wait ended because the thread was interrupted or the node is
     *         shutting down
     */
    private boolean awaitEmptyCache() {
        while (true) {
            if (Thread.currentThread().isInterrupted() || !ThreadPool.running.get()) {
                return false;
            }
            if (downloadCache.size() == 0) {
                return true;
            }
            try {
                Thread.sleep(CACHE_POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void switchToFork(Peer peer, List<Block> forkBlocks, long forkBlockId, Block forkBlock,
            BigInteger curCumulativeDifficulty) {
        // We remove blocks from chain back to where we start our fork
        // and save it in a list if we need to restore
        List<Block> myPoppedOffBlocks = chain.popOffTo(forkBlock, forkBlocks);
        logger.debug("Popped {} blocks", myPoppedOffBlocks.size());

        PushResult result = blockchain.getLastBlock().getId() == forkBlockId
                ? pushForkBlocks(peer, forkBlocks)
                : PushResult.NONE;
        int pushedForkBlocks = result.pushed();

        if (pushedForkBlocks > 0 && isWeakerThan(curCumulativeDifficulty)) {
            if (result.persistenceFailed()) {
                logger.warn("Fork could only be pushed partially, restoring own chain");
            } else {
                logger.warn("Fork was bad and pop off was caused by peer {}, blacklisting", peer.getPeerAddress());
                peer.blacklist("got a bad fork");
            }
            requeue(chain.popOffTo(forkBlock, null));
            pushedForkBlocks = 0;
        }

        // if we did not push any blocks we try to restore chain.
        if (pushedForkBlocks == 0) {
            restoreOwnChain(myPoppedOffBlocks);
        } else {
            requeue(myPoppedOffBlocks);
            logger.debug("Successfully switched to better chain.");
        }
    }

    private boolean isWeakerThan(BigInteger cumulativeDifficulty) {
        return blockchain.getLastBlock().getCumulativeDifficulty().compareTo(cumulativeDifficulty) < 0;
    }

    /** Pushes the fork blocks that connect to the current tip, until a push fails. */
    private PushResult pushForkBlocks(Peer peer, List<Block> forkBlocks) {
        int pushed = 0;
        for (Block block : forkBlocks) {
            if (blockchain.getLastBlock().getId() != block.getPreviousBlockId()) {
                continue;
            }
            try {
                blockService.preVerify(block, blockchain.getLastBlock());
                logPush(block);
                chain.pushBlock(block);
                pushed += 1;
            } catch (InterruptedException e) {
                // the remaining blocks would not connect to the tip anymore
                Thread.currentThread().interrupt();
                break;
            } catch (BlockNotAcceptedException e) {
                peer.blacklist(e, "during processing a fork");
                break;
            } catch (BlockNotPersistedException e) {
                // our database failed, not the peer: don't blacklist it
                logger.warn("Could not persist fork block {} at height {}, stopping fork processing",
                        block.getStringId(), block.getHeight());
                return new PushResult(pushed, true);
            }
        }
        return new PushResult(pushed, false);
    }

    private static void logPush(Block block) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        logger.debug("Pushing block {} generator {} sig {}", block.getHeight(),
                SignumID.fromLong(block.getGeneratorId()), Hex.toHexString(block.getBlockSignature()));
        logger.debug("Block timestamp {} base target {} difficulty {} commitment {}",
                block.getTimestamp(), block.getBaseTarget(), block.getCumulativeDifficulty(), block.getCommitment());
    }

    /**
     * Re-pushes our own popped off blocks, newest last. Transactions of blocks that could not
     * be restored go back to the unconfirmed pool.
     */
    private void restoreOwnChain(List<Block> myPoppedOffBlocks) {
        for (int i = myPoppedOffBlocks.size() - 1; i >= 0; i--) {
            if (!restoreBlock(myPoppedOffBlocks.get(i))) {
                // the failed block and all newer ones (lower indexes) are not on the chain
                List<Block> unrestored = myPoppedOffBlocks.subList(0, i + 1);
                logger.warn("Could not restore {} popped off blocks, returning their transactions to the unconfirmed pool",
                        unrestored.size());
                requeue(unrestored);
                return;
            }
        }
    }

    /** Re-pushes one of our own popped off blocks, returns {@code false} if that failed. */
    private boolean restoreBlock(Block block) {
        try {
            if (!block.isVerified()) {
                blockService.preVerify(block, blockchain.getLastBlock());
            }
            chain.pushBlock(block);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while restoring own popped off block {} at height {}",
                    block.getStringId(), block.getHeight());
        } catch (BlockNotAcceptedException e) {
            logger.warn("Popped off block no longer acceptable: " + JSON.toJsonString(block.getJsonObject()), e);
        } catch (BlockNotPersistedException e) {
            logger.warn("Could not persist own popped off block {} at height {} while restoring chain",
                    block.getStringId(), block.getHeight());
        }
        return false;
    }

    private void requeue(List<Block> blocks) {
        blocks.forEach(block -> transactionProcessor.processLater(block.getTransactions()));
    }

    private record PushResult(int pushed, boolean persistenceFailed) {
        static final PushResult NONE = new PushResult(0, false);
    }
}
