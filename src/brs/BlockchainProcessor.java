package brs;

import brs.listeners.NetVolumeListener;
import brs.listeners.PeerCountListener;
import brs.listeners.PerformanceListener;
import brs.listeners.QueueStatusListener;
import brs.peer.Peer;
import brs.util.JSON;
import brs.util.Observable;
import com.google.gson.JsonObject;
import java.util.List;

//TODO: Create JavaDocs and remove this
@SuppressWarnings({ "checkstyle:MissingJavadocTypeCheck", "checkstyle:MissingJavadocMethodCheck" })

public interface BlockchainProcessor extends Observable<Block, BlockchainProcessor.Event> {

    enum Event {
        BLOCK_PUSHED, BLOCK_POPPED, BLOCK_GENERATED, BLOCK_SCANNED,
        RESCAN_BEGIN, RESCAN_END,
        BEFORE_BLOCK_ACCEPT,
        BEFORE_BLOCK_APPLY, AFTER_BLOCK_APPLY
    }

    Peer getLastBlockchainFeeder();

    int getLastBlockchainFeederHeight();

    boolean isScanning();

    int getMinRollbackHeight();

    void processPeerBlock(JsonObject request, Peer peer) throws SignumException;

    void fullReset();

    void generateBlock(String secretPhrase, byte[] publicKey, Long nonce)
            throws BlockNotAcceptedException;

    void shutdown();

    void addQueueStatusListener(QueueStatusListener listener);

    void removeQueueStatusListener(QueueStatusListener listener);

    void addPerformanceListener(PerformanceListener listener);

    void removePerformanceListener(PerformanceListener listener);

    void addPeerCountListener(PeerCountListener listener);

    void removePeerCountListener(PeerCountListener listener);

    void addNetVolumeListener(NetVolumeListener listener);

    void removeNetVolumeListener(NetVolumeListener listener);

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
