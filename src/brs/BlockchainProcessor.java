package brs;

import brs.peer.Peer;
import brs.util.JSON;
import brs.util.Observable;
import com.google.gson.JsonObject;
import java.util.List;

public interface BlockchainProcessor extends Observable<Block, BlockchainProcessor.Event> {

    /**
     * Holds the status of the download/processing queue.
     * This includes the number of unverified blocks,
     * the number of verified blocks, and the total size of the queue.
     * This is used for monitoring the blockchain processing queue
     * and is updated periodically.
     * * The unverified size indicates how many blocks are waiting to be verified.
     * * The verified size indicates how many blocks have been verified
     * but not yet applied to the blockchain.
     * * The total size is the sum of unverified and verified sizes.
     */
    class QueueStatus {
        public final int unverifiedSize;
        public final int verifiedSize;
        public final int totalSize;

        public QueueStatus(int unverifiedSize, int verifiedSize, int totalSize) {
            this.unverifiedSize = unverifiedSize;
            this.verifiedSize = verifiedSize;
            this.totalSize = totalSize;
        }
    }

    /**
     * Holds performance statistics for the last processed block.
     * This includes the total processing time, database time,
     * application transaction time, and the block itself.
     * This is used for performance monitoring and debugging.
     * The times are in milliseconds.
     * This class is immutable and thread-safe.
     * It is created at the end of block processing and can be used
     * to analyze the performance of the blockchain processing.
     * It is also used to update the GUI with performance metrics.
     */
    class PerformanceStats {
        public final long totalTimeMs;
        public final long dbTimeMs;
        public final long atTimeMs;
        public final Block block;

        public PerformanceStats(long totalTimeMs, long dbTimeMs, long atTimeMs, Block block) {
            this.totalTimeMs = totalTimeMs;
            this.dbTimeMs = dbTimeMs;
            this.atTimeMs = atTimeMs;
            this.block = block;
        }
    }

    enum Event {
        BLOCK_PUSHED, BLOCK_POPPED, BLOCK_GENERATED, BLOCK_SCANNED,
        RESCAN_BEGIN, RESCAN_END,
        BEFORE_BLOCK_ACCEPT,
        BEFORE_BLOCK_APPLY, AFTER_BLOCK_APPLY,
        PEER_COUNT_CHANGED, NET_VOLUME_CHANGED, QUEUE_STATUS_CHANGED, PERFORMANCE_STATS_UPDATED
    }

    Peer getLastBlockchainFeeder();

    int getLastBlockchainFeederHeight();

    boolean isScanning();

    int getMinRollbackHeight();

    int getLastKnownPeerCount();

    int getLastKnownConnectedPeerCount();

    QueueStatus getQueueStatus();

    PerformanceStats getPerformanceStats();

    long getAccumulatedSyncTimeMs();

    long getAccumulatedSyncInProgressTimeMs();

    long getUploadedVolume();

    long getDownloadedVolume();

    void processPeerBlock(JsonObject request, Peer peer) throws SignumException;

    void fullReset();

    void generateBlock(String secretPhrase, byte[] publicKey, Long nonce)
            throws BlockNotAcceptedException;

    void shutdown();

    List<Block> popOffTo(int height);

    class BlockNotAcceptedException extends SignumException {

        BlockNotAcceptedException(String message) {
            super(message);
        }

    }

    class TransactionNotAcceptedException extends BlockNotAcceptedException {

        private final transient Transaction transaction;

        public TransactionNotAcceptedException(String message, Transaction transaction) {
            super(message + " transaction: " + JSON.toJsonString(transaction.getJsonObject()));
            this.transaction = transaction;
        }

        Transaction getTransaction() {
            return transaction;
        }

    }

    class BlockOutOfOrderException extends BlockNotAcceptedException {

        public BlockOutOfOrderException(String message) {
            super(message);
        }

    }

}
