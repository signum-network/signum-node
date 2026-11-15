package brs;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brs.peer.Peer;
import brs.util.DownloadCacheImpl;

/**
 * Continues a complete parallel download round with further rounds from the new tip, using the same
 * feeder and without the download handshake ({@code getCumulativeDifficulty} and
 * {@code getMilestoneBlockIds}). In a linear bulk round the next round starts at the tip the feeder
 * has just served, so the handshake would confirm nothing new.
 * <p>
 * The chain ends at the first incomplete or stopped round, when the feeder is no longer far enough
 * ahead, the cache is full, the download window is closed, the node shuts down, or after
 * {@link #MAX_CHAINED_ROUNDS}. The next download round then starts with a fresh handshake and a new
 * random feeder.
 */
final class ChainedRoundDownloader {

    private static final Logger logger = LoggerFactory.getLogger(ChainedRoundDownloader.class);

    /** Chained rounds per handshake, about 3,200 blocks with the default settings. */
    static final int MAX_CHAINED_ROUNDS = 8;

    private final ParallelBlockFetcher fetcher;
    private final DownloadedBlockAppender appender;
    private final DownloadCacheImpl downloadCache;
    private final IntUnaryOperator maxBlocksAhead;
    private final BooleanSupplier shuttingDown;

    /**
     * @param maxBlocksAhead maps the cache tip height to the number of blocks the download window
     *                       still allows
     * @param shuttingDown   true once the node is shutting down
     */
    ChainedRoundDownloader(ParallelBlockFetcher fetcher, DownloadedBlockAppender appender,
            DownloadCacheImpl downloadCache, IntUnaryOperator maxBlocksAhead, BooleanSupplier shuttingDown) {
        this.fetcher = fetcher;
        this.appender = appender;
        this.downloadCache = downloadCache;
        this.maxBlocksAhead = maxBlocksAhead;
        this.shuttingDown = shuttingDown;
    }

    /**
     * Downloads further rounds after {@code round}, as long as each round is complete.
     *
     * @param round           the segments of the round that was just appended
     * @param appended        the result of appending it
     * @param lockImporter    takes the importer batch lock before an append
     * @param releaseImporter releases it after the append
     * @return the result of the last append, or {@code appended} if no round followed
     */
    DownloadedBlockAppender.Result continueAfter(Peer feeder, int feederHeight,
            List<ParallelBlockFetcher.Segment> round, DownloadedBlockAppender.Result appended, Runnable lockImporter,
            Runnable releaseImporter) throws InterruptedException {
        DownloadedBlockAppender.Result result = appended;
        List<ParallelBlockFetcher.Segment> lastRound = round;
        for (int chained = 0; chained < MAX_CHAINED_ROUNDS; chained++) {
            Block tip = result.lastBlock();
            if (!isComplete(lastRound, result) || !mayContinue(tip.getHeight(), feederHeight)) {
                return result;
            }
            List<ParallelBlockFetcher.Segment> next = fetcher.fetch(feeder, downloadCache.getCumulativeDifficulty(),
                    tip.getHeight(), feederHeight, maxBlocksAhead.applyAsInt(tip.getHeight()));
            if (next.isEmpty()) {
                return result;
            }
            result = appendLocked(next, tip, lockImporter, releaseImporter);
            logger.debug("Chained download round {} after height {}", chained + 1, tip.getHeight());
            if (result.unlinkedHelper() != null) {
                fetcher.skipHelper(result.unlinkedHelper());
            }
            if (result.stopped()) {
                return result;
            }
            lastRound = next;
        }
        return result;
    }

    /**
     * A round is complete if it was not stopped, all its blocks were appended, its last segment is
     * full, and the download cache tip is its last block.
     */
    private boolean isComplete(List<ParallelBlockFetcher.Segment> round, DownloadedBlockAppender.Result result) {
        if (result.stopped() || round.isEmpty()) {
            return false;
        }
        ParallelBlockFetcher.Segment last = round.get(round.size() - 1);
        Block tip = result.lastBlock();
        return last.blocks().size() == fetcher.blocksPerPeer()
                && tip.getHeight() == last.fromHeight() + last.blocks().size()
                && downloadCache.getLastBlockId() == tip.getId();
    }

    private boolean mayContinue(int tipHeight, int feederHeight) {
        return !shuttingDown.getAsBoolean()
                && fetcher.isApplicable(true, tipHeight, feederHeight)
                && !downloadCache.isFull()
                && maxBlocksAhead.applyAsInt(tipHeight) > 0;
    }

    private DownloadedBlockAppender.Result appendLocked(List<ParallelBlockFetcher.Segment> segments, Block tip,
            Runnable lockImporter, Runnable releaseImporter) {
        lockImporter.run();
        try {
            return appender.append(segments, tip, true);
        } finally {
            releaseImporter.run();
        }
    }
}
