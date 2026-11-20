package brs;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import brs.BlockchainProcessor.BlockOutOfOrderException;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.peer.Peer;
import brs.services.BlockService;
import brs.services.ClockDriftMonitor;
import brs.util.DownloadCacheImpl;
import brs.util.JSON;
import brs.util.ThreadPool;

/**
 * Appends downloaded blocks to the download cache (or to the fork cache) during a download round.
 * The blocks come in segments, each from one peer; every block is attributed to the peer that sent
 * it, so a later failure blames the right peer. The blocks must link up from the common block on;
 * the round stops at the first block that does not.
 */
final class DownloadedBlockAppender {

    private static final Logger logger = LoggerFactory.getLogger(DownloadedBlockAppender.class);

    /** Parses a downloaded block; production uses {@link Block#parseBlock}. */
    interface BlockParser {
        Block parse(JsonObject blockData, int height) throws SignumException.ValidationException;
    }

    /**
     * @param lastBlock      the last block appended, or the common block if none was
     * @param stopped        true if the caller must end the round without further processing
     * @param unlinkedHelper the helper (peer of a segment after the first) whose blocks did not link,
     *                       or null
     */
    record Result(Block lastBlock, boolean stopped, Peer unlinkedHelper) {

        Result(Block lastBlock, boolean stopped) {
            this(lastBlock, stopped, null);
        }
    }

    private final DownloadCacheImpl downloadCache;
    private final Blockchain blockchain;
    private final BlockService blockService;
    private final Supplier<FluxCapacitor> fluxCapacitor;
    private final BlockParser blockParser;
    private final ReentrantReadWriteLock blockImporterLock;
    private final boolean decoupledSync;
    private final ClockDriftMonitor clockDriftMonitor;

    DownloadedBlockAppender(DownloadCacheImpl downloadCache, Blockchain blockchain, BlockService blockService,
            Supplier<FluxCapacitor> fluxCapacitor, BlockParser blockParser, ReentrantReadWriteLock blockImporterLock,
            boolean decoupledSync, ClockDriftMonitor clockDriftMonitor) {
        this.downloadCache = downloadCache;
        this.blockchain = blockchain;
        this.blockService = blockService;
        this.fluxCapacitor = fluxCapacitor;
        this.blockParser = blockParser;
        this.blockImporterLock = blockImporterLock;
        this.decoupledSync = decoupledSync;
        this.clockDriftMonitor = clockDriftMonitor;
    }

    /**
     * Appends the blocks of all segments in order, starting after {@code commonBlock}. Stops
     * iterating, without stopping the round, once the download window is reached.
     *
     * @param saveInCache true to append to the download cache, false to collect fork blocks
     */
    Result append(List<ParallelBlockFetcher.Segment> segments, Block commonBlock, boolean saveInCache) {
        Block lastBlock = commonBlock;
        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            ParallelBlockFetcher.Segment segment = segments.get(segmentIndex);
            boolean firstOfSegment = true;
            for (JsonElement blockJson : segment.blocks()) {
                int height = lastBlock.getHeight() + 1;
                if (fluxCapacitor.get().getValue(FluxValues.POC_PLUS, height)
                        && height - blockchain.getHeight() >= Constants.MAX_ROLLBACK) {
                    logger.debug("GetMoreBlocks, wait for other threads to catch up");
                    return new Result(lastBlock, false);
                }
                JsonObject blockData = JSON.getAsJsonObject(blockJson);
                try {
                    Block block = blockParser.parse(blockData, height);
                    clockDriftMonitor.onBlockReceived(block.getId());
                    // Make sure it maps back to chain
                    if (lastBlock.getId() != block.getPreviousBlockId()) {
                        logLinkBreak(segment, segmentIndex > 0 && firstOfSegment, lastBlock, block);
                        return new Result(lastBlock, true, segmentIndex > 0 ? segment.peer() : null);
                    }
                    // set height and cumulative difficulty to block
                    block.setHeight(height);
                    block.setPeer(segment.peer());
                    block.setByteLength(JSON.toJsonString(blockData).length());
                    calculateBaseTarget(block, lastBlock);
                    if (saveInCache) {
                        if (downloadCache.getLastBlockId() == block.getPreviousBlockId()) {
                            // ↑ still maps back? we might have got announced/forged blocks
                            if (!downloadCache.addBlock(block)) {
                                // we stop the loop since the cache has been locked
                                return new Result(lastBlock, true);
                            }
                            if (logger.isDebugEnabled()) {
                                logger.debug("Added from download: Id: {} Height: {}", block.getId(),
                                        block.getHeight());
                            }
                        }
                    } else {
                        downloadCache.addForkBlock(block);
                    }
                    lastBlock = block;
                } catch (BlockOutOfOrderException e) {
                    logger.info(e.toString() + " - autoflushing cache to get rid of it", e);
                    downloadCache.resetCache();
                    return new Result(lastBlock, true);
                } catch (RuntimeException | SignumException.ValidationException e) {
                    logger.info("Failed to parse block: {}", e.getMessage());
                    if (logger.isDebugEnabled()) {
                        logger.debug("Failed to parse block trace: {}", Arrays.toString(e.getStackTrace()));
                    }
                    segment.peer().blacklist(e, "pulled invalid data using getCumulativeDifficulty");
                    return new Result(lastBlock, true);
                } catch (Exception e) {
                    logger.warn("Unhandled exception {}" + e.toString(), e);
                    logger.warn("Unhandled exception trace: {}", Arrays.toString(e.getStackTrace()));
                }
                firstOfSegment = false;
                // executor shutdown?
                if (Thread.currentThread().isInterrupted() || !ThreadPool.running.get()) {
                    return new Result(lastBlock, true);
                }
            }
        }
        return new Result(lastBlock, false);
    }

    private void calculateBaseTarget(Block block, Block lastBlock) throws BlockOutOfOrderException {
        if (decoupledSync) {
            // the download thread holds the importer read lock for the whole batch
            blockService.calculateBaseTarget(block, lastBlock);
            return;
        }
        blockImporterLock.readLock().lock();
        try {
            blockService.calculateBaseTarget(block, lastBlock);
        } finally {
            blockImporterLock.readLock().unlock();
        }
    }

    private static void logLinkBreak(ParallelBlockFetcher.Segment segment, boolean atSegmentBoundary,
            Block lastBlock, Block block) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        if (atSegmentBoundary) {
            logger.debug("Segment from {} after height {} does not link to the previous segment, dropping the rest"
                    + " of the round", segment.peer().getPeerAddress(), segment.fromHeight());
            return;
        }
        logger.debug("Discarding downloaded data. Last downloaded blocks is rubbish");
        logger.debug("DB blockID: {} DB blockheight: {} Downloaded previd: {}", lastBlock.getId(),
                lastBlock.getHeight(), block.getPreviousBlockId());
    }
}
