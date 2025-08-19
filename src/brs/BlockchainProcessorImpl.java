package brs;

import static brs.Constants.FEE_QUANT_SIP3;
import static brs.Constants.ONE_SIGNA;

import brs.at.*;
import brs.crypto.Crypto;
import brs.db.BlockDb;
import brs.db.DerivedTable;
import brs.db.TransactionDb;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.store.BlockchainStore;
import brs.db.store.DerivedTableManager;
import brs.db.store.Stores;
import brs.fluxcapacitor.FluxValues;
import brs.events.NetVolumeChangedEvent;
import brs.events.PeerCountChangedEvent;
import brs.events.PerformanceStatsUpdatedEvent;
import brs.events.QueueStatusEvent;
import brs.peer.Peer;
import brs.peer.Peers;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.AccountService;
import brs.services.AliasService;
import brs.services.BlockService;
import brs.services.EscrowService;
import brs.services.IndirectIncomingService;
import brs.services.SubscriptionService;
import brs.services.TimeService;
import brs.services.TransactionService;
import brs.statistics.StatisticsManagerImpl;
import brs.transactionduplicates.TransactionDuplicatesCheckerImpl;
import brs.unconfirmedtransactions.UnconfirmedTransactionStore;
import brs.util.Convert;
import brs.util.DownloadCacheImpl;
import brs.util.EventBus;
import brs.util.JSON;
import brs.util.Listener;
import brs.util.Listeners;
import brs.util.ThreadPool;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import signumj.entity.SignumID;

//TODO: Create JavaDocs and remove this
@SuppressWarnings({
        "checkstyle:MissingJavadocTypeCheck",
        "checkstyle:MissingJavadocMethodCheck",
        "checkstyle:LineLengthCheck" })

public final class BlockchainProcessorImpl implements BlockchainProcessor {

    private final Logger logger = LoggerFactory.getLogger(BlockchainProcessorImpl.class);
    private final Stores stores;
    private final BlockchainImpl blockchain;
    private final BlockService blockService;
    private final AccountService accountService;
    private final SubscriptionService subscriptionService;
    private final EscrowService escrowService;
    private final TimeService timeService;
    private final TransactionService transactionService;
    private final PropertyService propertyService;
    private final TransactionProcessorImpl transactionProcessor;
    private final EconomicClustering economicClustering;
    private final BlockchainStore blockchainStore;
    private final BlockDb blockDb;
    private final TransactionDb transactionDb;
    private final DownloadCacheImpl downloadCache;
    private final DerivedTableManager derivedTableManager;
    private final StatisticsManagerImpl statisticsManager;
    private final Generator generator;
    private final DBCacheManagerImpl dbCacheManager;
    private final IndirectIncomingService indirectIncomingService;
    private final long genesisBlockId;

    private static final int MAX_TIMESTAMP_DIFFERENCE = 15;
    private boolean oclVerify;
    private final int oclUnverifiedQueue;

    private final Semaphore gpuUsage = new Semaphore(2);

    private final boolean trimDerivedTables;
    private final AtomicInteger lastTrimHeight = new AtomicInteger();

    private final Listeners<Block, Event> blockListeners = new Listeners<>();
    private final AtomicReference<Peer> lastBlockchainFeeder = new AtomicReference<>();
    private final AtomicInteger lastBlockchainFeederHeight = new AtomicInteger();
    private final AtomicBoolean getMoreBlocks = new AtomicBoolean(true);

    private final AtomicBoolean isScanning = new AtomicBoolean(false);

    private final AtomicBoolean isConsistent = new AtomicBoolean(false);

    private int lastKnownPeerCount = -1;
    private int lastKnownConnectedPeerCount = -1;

    private int autoPopOffLastStuckHeight = 0;
    private int autoPopOffNumberOfBlocks = 0;
    private ATProcessorCache atProcessorCache = ATProcessorCache.getInstance();
    private Long atTimeNanos;
    private long atTimeMs;

    private int lastTotalSize = 0;
    private int lastUnverifiedQueueSize = 0;

    private final Listener<Peer> peerListener = peer -> updateAndFirePeerCount();

    private final Listener<Peer> netVolumeListener = peer -> updateAndFireNetVolume();

    private final boolean autoPopOffEnabled;

    private EventBus eventBus = Signum.getEventBus();

    @Override
    public void shutdown() {
        logger.info("Shutting down blockchain processor...");
        blockListeners.clear();
        for (Peers.Event event : Peers.Event.values()) {
            Peers.removeListener(peerListener, event);
        }
        if (getOclVerify()) {
            logger.info("Destroying OCLPoC instance from BlockchainProcessor.");
            OCLPoC.destroy();
        }
    }

    public final void setOclVerify(Boolean b) {
        oclVerify = b;
    }

    public Boolean getOclVerify() {
        return oclVerify;
    }

    // --- Firing events ---

    public BlockchainProcessorImpl(ThreadPool threadPool,
            BlockService blockService,
            TransactionProcessorImpl transactionProcessor,
            BlockchainImpl blockchain,
            PropertyService propertyService,
            SubscriptionService subscriptionService,
            TimeService timeService,
            DerivedTableManager derivedTableManager,
            BlockDb blockDb,
            TransactionDb transactionDb,
            EconomicClustering economicClustering,
            BlockchainStore blockchainStore,
            Stores stores,
            EscrowService escrowService,
            TransactionService transactionService,
            DownloadCacheImpl downloadCache,
            Generator generator,
            StatisticsManagerImpl statisticsManager,
            DBCacheManagerImpl dbCacheManager,
            AccountService accountService,
            IndirectIncomingService indirectIncomingService,
            AliasService aliasService) {
        this.blockService = blockService;
        this.transactionProcessor = transactionProcessor;
        this.timeService = timeService;
        this.derivedTableManager = derivedTableManager;
        this.blockDb = blockDb;
        this.transactionDb = transactionDb;
        this.blockchain = blockchain;
        this.subscriptionService = subscriptionService;
        this.blockchainStore = blockchainStore;
        this.stores = stores;
        this.downloadCache = downloadCache;
        this.generator = generator;
        this.economicClustering = economicClustering;
        this.escrowService = escrowService;
        this.transactionService = transactionService;
        this.statisticsManager = statisticsManager;
        this.dbCacheManager = dbCacheManager;
        this.accountService = accountService;
        this.indirectIncomingService = indirectIncomingService;
        this.propertyService = propertyService;

        for (Peers.Event event : Peers.Event.values()) {
            Peers.listeners.addListener(peerListener, event);
        }

        Peers.listeners.addListener(netVolumeListener, Peers.Event.UPLOADED_VOLUME);
        Peers.listeners.addListener(netVolumeListener, Peers.Event.DOWNLOADED_VOLUME);

        autoPopOffEnabled = propertyService.getBoolean(Props.AUTO_POP_OFF_ENABLED);

        oclVerify = propertyService.getBoolean(Props.GPU_ACCELERATION); // use GPU acceleration ?
        oclUnverifiedQueue = propertyService.getInt(Props.GPU_UNVERIFIED_QUEUE);

        trimDerivedTables = propertyService.getBoolean(Props.DB_TRIM_DERIVED_TABLES);
        genesisBlockId = Convert.parseUnsignedLong(
                propertyService.getString(Props.GENESIS_BLOCK_ID));

        blockListeners.addListener(block -> {
            if (block.getHeight() % 5000 == 0) {
                logger.info("processed block {}", block.getHeight());
            }
        }, Event.BLOCK_SCANNED);

        blockListeners.addListener(block -> {
            if (block.getHeight() % 5000 == 0) {
                logger.info("processed block {}", block.getHeight());
                // Db.analyzeTables(); no-op
            }
        }, Event.BLOCK_PUSHED);

        blockListeners.addListener(
                block -> transactionProcessor.revalidateUnconfirmedTransactions(),
                Event.BLOCK_PUSHED);
        if (trimDerivedTables) {
            blockListeners.addListener(block -> {
                if (block.getHeight() % (Constants.MAX_ROLLBACK * 10) == 0
                        && lastTrimHeight.get() > 0) {
                    logger.debug("Trimming derived tables...");
                    this.derivedTableManager.getDerivedTables()
                            .forEach(table -> table.trim(lastTrimHeight.get()));
                }
            }, Event.AFTER_BLOCK_APPLY);
        }

        blockListeners.addListener(block -> {
            updateAndFireQueueStatus();
        }, Event.BLOCK_PUSHED);

        addGenesisBlock();
        if (Boolean.FALSE.equals(propertyService.getBoolean(Props.DB_SKIP_CHECK))
                && checkDatabaseState() != 0) {
            logger.warn("Database is inconsistent,"
                    + "try to pop off to block height {} or sync from empty.",
                    getMinRollbackHeight());
        }

        Runnable getMoreBlocksThread = new Runnable() {
            private JsonElement getCumulativeDifficultyRequest;

            private boolean peerHasMore;

            @Override
            public void run() {
                if (propertyService.getBoolean(Props.DEV_OFFLINE)) {
                    return;
                }
                if (getCumulativeDifficultyRequest == null) {
                    JsonObject request = new JsonObject();
                    request.addProperty("requestType", "getCumulativeDifficulty");
                    getCumulativeDifficultyRequest = JSON.prepareRequest(request);
                }
                while (!Thread.currentThread().isInterrupted() && ThreadPool.running.get()) {
                    try {
                        try {
                            if (!getMoreBlocks.get()) {
                                return;
                            }

                            if (downloadCache.isFull()) {
                                return;
                            }

                            // Keep the download cache below the rollback limit
                            int cacheHeight = downloadCache.getLastBlock().getHeight();
                            if (Signum.getFluxCapacitor().getValue(FluxValues.POC_PLUS, cacheHeight)
                                    && cacheHeight - blockchain.getHeight() > Constants.MAX_ROLLBACK / 2) {
                                logger.debug("GetMoreBlocks, skip download, wait for other threads to catch up");
                                return;
                            }

                            peerHasMore = true;
                            BigInteger curCumulativeDifficulty = downloadCache.getCumulativeDifficulty();
                            BigInteger betterCumulativeDifficulty = BigInteger.ZERO;

                            Peer peer = null;
                            do {
                                peer = Peers.getAnyPeer(Peer.State.CONNECTED);
                                if (peer == null) {
                                    logger.debug("No peer connected.");
                                    return;
                                }
                                if (!peer.isHigherOrEqualVersionThan(
                                        Signum.getFluxCapacitor().getValue(FluxValues.MIN_PEER_VERSION))
                                        || (peer.getNetworkName() != null && !peer.getNetworkName()
                                                .equals(propertyService.getString(Props.NETWORK_NAME)))) {
                                    // ignore this peer, it will be removed by the peers discovery thread
                                    continue;
                                }

                                JsonObject response = peer.send(getCumulativeDifficultyRequest);
                                if (response == null) {
                                    continue;
                                }
                                if (response.get("blockchainHeight") != null) {
                                    lastBlockchainFeeder.set(peer);
                                    lastBlockchainFeederHeight.set(JSON.getAsInt(response.get("blockchainHeight")));
                                } else {
                                    logger.debug("Peer {} has no chainheight", peer.getAnnouncedAddress());
                                    continue;
                                }

                                /* Cache now contains Cumulative Difficulty */
                                String peerCumulativeDifficulty = JSON
                                        .getAsString(response.get("cumulativeDifficulty"));
                                if (peerCumulativeDifficulty == null) {
                                    logger.debug("Peer CumulativeDifficulty is null");
                                    continue;
                                }
                                betterCumulativeDifficulty = new BigInteger(peerCumulativeDifficulty);
                            } while (betterCumulativeDifficulty.compareTo(curCumulativeDifficulty) <= 0);

                            logger.trace("Got a better cumulative difficulty {} than current {}.",
                                    betterCumulativeDifficulty, curCumulativeDifficulty);

                            long commonBlockId = genesisBlockId;
                            long cacheLastBlockId = downloadCache.getLastBlockId();

                            // Now we will find the highest common block between ourself and our peer
                            if (cacheLastBlockId != genesisBlockId) {
                                commonBlockId = getCommonMilestoneBlockId(peer);
                                if (commonBlockId == 0 || !peerHasMore) {
                                    logger.debug("We could not get a common milestone block from peer.");
                                    return;
                                }
                            }

                            // unlocking cache for writing.
                            // This must be done before we query where to add blocks.
                            // We sync the cache in event of pop off
                            synchronized (BlockchainProcessorImpl.this.downloadCache) {
                                downloadCache.unlockCache();
                            }

                            /*
                             * if we did not get the last block in chain as common block we will be
                             * downloading a
                             * fork. however if it is to far off we cannot process it anyway. canBeFork will
                             * check
                             * where in chain this common block is fitting and return true if it is worth to
                             * continue.
                             */
                            boolean saveInCache = true;
                            if (commonBlockId != cacheLastBlockId) {
                                if (downloadCache.canBeFork(commonBlockId)) {
                                    // the fork is not that old. Lets see if we can get more precise.
                                    commonBlockId = getCommonBlockId(peer, commonBlockId);
                                    if (commonBlockId == 0 || !peerHasMore) {
                                        logger.debug("Trying to get a more precise common block resulted in an error.");
                                        return;
                                    }
                                    saveInCache = false;
                                    downloadCache.resetForkBlocks();
                                } else {
                                    if (logger.isDebugEnabled()) {
                                        logger.debug("A peer wants to feed us a fork that is more than "
                                                + Constants.MAX_ROLLBACK + " blocks old.");
                                    }
                                    peer.blacklist("feeding us a too old fork");
                                    return;
                                }
                            }

                            JsonArray nextBlocks = getNextBlocks(peer, commonBlockId);
                            if (nextBlocks == null || nextBlocks.isEmpty()) {
                                logger.debug("Peer did not feed us any blocks");
                                return;
                            }

                            // download blocks from peer
                            Block lastBlock = downloadCache.getBlock(commonBlockId);
                            if (lastBlock == null) {
                                logger.info("Error: lastBlock is null");
                                return;
                            }

                            // loop blocks and make sure they fit in chain
                            Block block;
                            JsonObject blockData;

                            for (JsonElement o : nextBlocks) {
                                int height = lastBlock.getHeight() + 1;
                                blockData = JSON.getAsJsonObject(o);
                                try {
                                    if (Signum.getFluxCapacitor().getValue(FluxValues.POC_PLUS, height)
                                            && height - blockchain.getHeight() >= Constants.MAX_ROLLBACK) {
                                        logger.debug("GetMoreBlocks, wait for other threads to catch up");
                                        break;
                                    }
                                    block = Block.parseBlock(blockData, height);
                                    // Make sure it maps back to chain
                                    if (lastBlock.getId() != block.getPreviousBlockId()) {
                                        logger.debug("Discarding downloaded data. Last downloaded blocks is rubbish");
                                        logger.debug("DB blockID: {} DB blockheight: {} Downloaded previd: {}",
                                                lastBlock.getId(), lastBlock.getHeight(), block.getPreviousBlockId());
                                        return;
                                    }
                                    // set height and cumulative difficulty to block
                                    block.setHeight(height);
                                    block.setPeer(peer);
                                    block.setByteLength(JSON.toJsonString(blockData).length());
                                    blockService.calculateBaseTarget(block, lastBlock);
                                    if (saveInCache) {
                                        if (downloadCache.getLastBlockId() == block.getPreviousBlockId()) {
                                            // ↑ still maps back? we might have got announced/forged blocks
                                            if (!downloadCache.addBlock(block)) {
                                                // we stop the loop since cahce has been locked
                                                return;
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
                                    return;
                                } catch (RuntimeException | SignumException.ValidationException e) {
                                    logger.info("Failed to parse block: {}", e.getMessage());
                                    if (logger.isDebugEnabled()) {
                                        logger.debug("Failed to parse block trace: {}",
                                                Arrays.toString(e.getStackTrace()));
                                    }
                                    peer.blacklist(e, "pulled invalid data using getCumulativeDifficulty");
                                    return;
                                } catch (Exception e) {
                                    logger.warn("Unhandled exception {}" + e.toString(), e);
                                    logger.warn("Unhandled exception trace: {}", Arrays.toString(e.getStackTrace()));
                                }
                                // executor shutdown?
                                if (Thread.currentThread().isInterrupted() || !ThreadPool.running.get()) {
                                    return;
                                }
                            } // end block loop

                            if (logger.isTraceEnabled()) {
                                logger.trace("Unverified blocks: {}", downloadCache.getUnverifiedSize());
                                logger.trace("Blocks in cache: {}", downloadCache.size());
                                logger.trace("Bytes in cache: {}", downloadCache.getBlockCacheSize());
                            }
                            if (!saveInCache) {
                                /*
                                 * Since we cannot rely on peers reported cumulative difficulty we do
                                 * a final check to see that the CumulativeDifficulty actually is bigger
                                 * before we do a popOff and switch chain.
                                 */
                                if (lastBlock.getCumulativeDifficulty().compareTo(curCumulativeDifficulty) < 0) {
                                    peer.blacklist(
                                            "peer claimed to have bigger cumulative difficulty but in reality it did not.");
                                    downloadCache.resetForkBlocks();
                                    break;
                                }
                                processFork(peer, downloadCache.getForkList(), commonBlockId);
                            }

                        } catch (SignumException.StopException e) {
                            logger.info("Blockchain download stopped: {}", e.getMessage());
                        } catch (InterruptedException ignored) {
                            // shutting down
                        } catch (Exception e) {
                            logger.info("Error in blockchain download thread", e);
                        } // end second try
                    } catch (Exception t) {
                        logger.info("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString(), t);
                        System.exit(1);
                    } // end first try
                } // end while
            }

            private long getCommonMilestoneBlockId(Peer peer) throws InterruptedException {

                String lastMilestoneBlockId = null;

                while (!Thread.currentThread().isInterrupted() && ThreadPool.running.get()) {
                    JsonObject milestoneBlockIdsRequest = new JsonObject();
                    milestoneBlockIdsRequest.addProperty("requestType", "getMilestoneBlockIds");
                    if (lastMilestoneBlockId == null) {
                        milestoneBlockIdsRequest.addProperty("lastBlockId",
                                Convert.toUnsignedLong(downloadCache.getLastBlockId()));
                    } else {
                        milestoneBlockIdsRequest.addProperty("lastMilestoneBlockId", lastMilestoneBlockId);
                    }

                    JsonObject response = peer.send(JSON.prepareRequest(milestoneBlockIdsRequest));
                    if (response == null) {
                        logger.debug("Got null response in getCommonMilestoneBlockId");
                        return 0;
                    }
                    JsonArray milestoneBlockIds = JSON.getAsJsonArray(response.get("milestoneBlockIds"));
                    if (milestoneBlockIds == null) {
                        logger.debug("MilestoneArray is null");
                        return 0;
                    }
                    if (milestoneBlockIds.size() == 0) {
                        return genesisBlockId;
                    }
                    // prevent overloading with blockIds
                    if (milestoneBlockIds.size() > 20) {
                        peer.blacklist("obsolete or rogue peer sends too many milestoneBlockIds");
                        return 0;
                    }
                    if (Boolean.TRUE.equals(JSON.getAsBoolean(response.get("last")))) {
                        peerHasMore = false;
                    }

                    for (JsonElement milestoneBlockId : milestoneBlockIds) {
                        long blockId = Convert.parseUnsignedLong(JSON.getAsString(milestoneBlockId));

                        if (downloadCache.hasBlock(blockId)) {
                            if (lastMilestoneBlockId == null && milestoneBlockIds.size() > 1) {
                                peerHasMore = false;
                                logger.debug("Peer dont have more (cache)");
                            }
                            return blockId;
                        }
                        lastMilestoneBlockId = JSON.getAsString(milestoneBlockId);
                    }
                }
                throw new InterruptedException("interrupted");
            }

            private long getCommonBlockId(Peer peer, long commonBlockId) throws InterruptedException {

                while (!Thread.currentThread().isInterrupted() && ThreadPool.running.get()) {
                    JsonObject request = new JsonObject();
                    request.addProperty("requestType", "getNextBlockIds");
                    request.addProperty("blockId", Convert.toUnsignedLong(commonBlockId));
                    JsonObject response = peer.send(JSON.prepareRequest(request));
                    if (response == null) {
                        return 0;
                    }
                    JsonArray nextBlockIds = JSON.getAsJsonArray(response.get("nextBlockIds"));
                    if (nextBlockIds == null || nextBlockIds.size() == 0) {
                        return 0;
                    }
                    // prevent overloading with blockIds
                    if (nextBlockIds.size() > 1440) {
                        peer.blacklist("obsolete or rogue peer sends too many nextBlocks");
                        return 0;
                    }

                    for (JsonElement nextBlockId : nextBlockIds) {
                        long blockId = Convert.parseUnsignedLong(JSON.getAsString(nextBlockId));
                        if (!downloadCache.hasBlock(blockId)) {
                            return commonBlockId;
                        }
                        commonBlockId = blockId;
                    }
                }

                throw new InterruptedException("interrupted");
            }

            private JsonArray getNextBlocks(Peer peer, long curBlockId) {

                JsonObject request = new JsonObject();
                request.addProperty("requestType", "getNextBlocks");
                request.addProperty("blockId", Convert.toUnsignedLong(curBlockId));
                if (logger.isDebugEnabled()) {
                    logger.debug("Getting next Blocks after {} from {}", Convert.toUnsignedLong(curBlockId),
                            peer.getPeerAddress());
                }
                JsonObject response = peer.send(JSON.prepareRequest(request));
                if (response == null) {
                    return null;
                }

                JsonArray nextBlocks = JSON.getAsJsonArray(response.get("nextBlocks"));
                if (nextBlocks == null) {
                    return null;
                }
                // prevent overloading with blocks
                if (nextBlocks.size() > 1440) {
                    peer.blacklist("obsolete or rogue peer sends too many nextBlocks");
                    return null;
                }
                logger.debug("Got {} blocks after {} from {}", nextBlocks.size(), curBlockId, peer.getPeerAddress());
                return nextBlocks;

            }

            private void processFork(Peer peer, final List<Block> forkBlocks, long forkBlockId) {
                logger.debug("A fork is detected. Waiting for cache to be processed.");
                downloadCache.lockCache(); // dont let anything add to cache!
                while (!Thread.currentThread().isInterrupted() && ThreadPool.running.get()) {
                    if (downloadCache.size() == 0) {
                        break;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
                synchronized (BlockchainProcessorImpl.this.downloadCache) {
                    synchronized (transactionProcessor.getUnconfirmedTransactionsSyncObj()) {
                        logger.debug("Cache is now processed. Starting to process fork.");
                        Block forkBlock = blockchain.getBlock(forkBlockId);

                        // we read the current cumulative difficulty
                        BigInteger curCumulativeDifficulty = blockchain.getLastBlock().getCumulativeDifficulty();

                        // We remove blocks from chain back to where we start our fork
                        // and save it in a list if we need to restore
                        List<Block> myPoppedOffBlocks = popOffTo(forkBlock, forkBlocks);
                        logger.debug("Popped {} blocks", myPoppedOffBlocks.size());

                        // now we check that our chain is popped off.
                        // If all seems ok is we try to push fork.
                        int pushedForkBlocks = 0;
                        if (blockchain.getLastBlock().getId() == forkBlockId) {
                            for (Block block : forkBlocks) {
                                if (blockchain.getLastBlock().getId() == block.getPreviousBlockId()) {
                                    try {
                                        blockService.preVerify(block, blockchain.getLastBlock());

                                        logger.debug("Pushing block {} generator {} sig {}", block.getHeight(),
                                                SignumID.fromLong(block.getGeneratorId()),
                                                Hex.toHexString(block.getBlockSignature()));
                                        logger.debug("Block timestamp {} base target {} difficulty {} commitment {}",
                                                block.getTimestamp(), block.getBaseTarget(),
                                                block.getCumulativeDifficulty(), block.getCommitment());

                                        pushBlock(block);
                                        pushedForkBlocks += 1;
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    } catch (BlockNotAcceptedException e) {
                                        peer.blacklist(e, "during processing a fork");
                                        break;
                                    }
                                }
                            }
                        }

                        /*
                         * we check if we succeeded to push any block. if we did we check against
                         * cumulative
                         * difficulty If it is lower we blacklist peer and set chain to be processed
                         * later.
                         */
                        if (pushedForkBlocks > 0 && blockchain.getLastBlock().getCumulativeDifficulty()
                                .compareTo(curCumulativeDifficulty) < 0) {
                            logger.warn("Fork was bad and pop off was caused by peer {}, blacklisting",
                                    peer.getPeerAddress());
                            peer.blacklist("got a bad fork");
                            List<Block> peerPoppedOffBlocks = popOffTo(forkBlock, null);
                            pushedForkBlocks = 0;
                            peerPoppedOffBlocks
                                    .forEach(block -> transactionProcessor.processLater(block.getTransactions()));
                        }

                        // if we did not push any blocks we try to restore chain.
                        if (pushedForkBlocks == 0) {
                            for (int i = myPoppedOffBlocks.size() - 1; i >= 0; i--) {
                                Block block = myPoppedOffBlocks.remove(i);
                                try {
                                    blockService.preVerify(block, blockchain.getLastBlock());
                                    pushBlock(block);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                } catch (BlockNotAcceptedException e) {
                                    logger.warn("Popped off block no longer acceptable: "
                                            + JSON.toJsonString(block.getJsonObject()), e);
                                    break;
                                }
                            }
                        } else {
                            myPoppedOffBlocks
                                    .forEach(block -> transactionProcessor.processLater(block.getTransactions()));
                            logger.debug("Successfully switched to better chain.");
                        }
                        logger.info("Forkprocessing complete.");
                        downloadCache.resetForkBlocks();
                        downloadCache.resetCache(); // Reset and set cached vars to chaindata.
                    }
                }
            }
        };
        threadPool.scheduleThread("GetMoreBlocks", getMoreBlocksThread,
                propertyService.getInt(Props.BLOCK_PROCESS_THREAD_DELAY),
                TimeUnit.MILLISECONDS);
        /* this should fetch first block in cache */
        // resetting cache because we have blocks that cannot be processed.
        // pushblock removes the block from cache.
        Runnable blockImporterThread = () -> {
            while (!Thread.interrupted() && ThreadPool.running.get() && downloadCache.size() > 0) {
                try {
                    Block lastBlock = blockchain.getLastBlock();
                    Long lastId = lastBlock.getId();
                    Block currentBlock = downloadCache.getNextBlock(lastId); /*
                                                                              * this should fetch first block in cache
                                                                              */
                    if (currentBlock == null || currentBlock.getHeight() != (lastBlock.getHeight() + 1)) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("cache is reset due to orphaned block(s). CacheSize: {}",
                                    downloadCache.size());
                        }
                        downloadCache.resetCache(); // resetting cache because we have blocks that cannot be processed.
                        break;
                    }
                    try {
                        if (!currentBlock.isVerified()) {
                            downloadCache.removeUnverified(currentBlock.getId());
                            blockService.preVerify(currentBlock, lastBlock);
                            logger.debug("block was not preverified");
                        }
                        pushBlock(currentBlock); // pushblock removes the block from cache.
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (BlockNotAcceptedException e) {
                        logger.error("Block not accepted", e);
                        blacklistClean(currentBlock, e, "found invalid pull/push data during importing the block");
                        autoPopOff(currentBlock.getHeight());
                        break;
                    }
                } catch (Exception exception) {
                    if (exception.toString().contains("[SQLITE_BUSY]")
                            || exception.toString().contains("[SQLITE_BUSY_SNAPSHOT]")) {
                        logger.warn("SQLite busy, trying again later...");
                    } else {
                        exception.printStackTrace();
                        logger.error("Uncaught exception in blockImporterThread", exception);
                    }
                }
            }
        };
        threadPool.scheduleThread("ImportBlocks", blockImporterThread,
                propertyService.getInt(Props.BLOCK_PROCESS_THREAD_DELAY),
                TimeUnit.MILLISECONDS);

        // Is there anything to verify
        // should we use Ocl?
        // is Ocl ready ?
        // verify using java
        Runnable pocVerificationThread = () -> {
            boolean verifyWithOcl;
            int queueThreshold = oclVerify ? oclUnverifiedQueue : 0;

            while (!Thread.interrupted() && ThreadPool.running.get()) {

                updateAndFireQueueStatus();
                int unVerified = downloadCache.getUnverifiedSize();
                if (unVerified > queueThreshold) { // Is there anything to verify
                    if (unVerified >= oclUnverifiedQueue && oclVerify) { // should we use Ocl?
                        verifyWithOcl = true;
                        if (!gpuUsage.tryAcquire()) { // is Ocl ready ?
                            logger.debug("already max locked");
                            verifyWithOcl = false;
                        }
                    } else {
                        verifyWithOcl = false;
                    }
                    if (verifyWithOcl) {
                        int pocVersion;
                        int pos = 0;
                        HashMap<Block, Block> blocks = new HashMap<>();
                        pocVersion = downloadCache.getPocVersion(downloadCache.getUnverifiedBlockIdFromPos(0));
                        while (!Thread.interrupted() && ThreadPool.running.get()
                                && (downloadCache.getUnverifiedSize() - 1) > pos
                                && blocks.size() < OCLPoC.getMaxItems()) {
                            long blockId = downloadCache.getUnverifiedBlockIdFromPos(pos);
                            if (downloadCache.getPocVersion(blockId) != pocVersion) {
                                break;
                            }
                            Block block = downloadCache.getBlock(blockId);
                            Block prevBlock = downloadCache.getBlock(block.getPreviousBlockId());
                            if (prevBlock == null) {
                                prevBlock = blockchain.getBlock(block.getPreviousBlockId());
                            }
                            blocks.put(block, prevBlock);
                            pos += 1;
                        }
                        try {
                            OCLPoC.validatePoC(blocks, pocVersion, blockService);
                            downloadCache.removeUnverifiedBatch(blocks.keySet());
                            updateAndFireQueueStatus();
                        } catch (OCLPoC.PreValidateFailException e) {
                            logger.info(e.toString(), e);
                            blacklistClean(e.getBlock(), e,
                                    "found invalid pull/push data during processing the pocVerification");
                            updateAndFireQueueStatus();
                        } catch (OCLPoC.OCLCheckerException e) {
                            logger.info("Open CL error. slow verify will occur for the next " + oclUnverifiedQueue
                                    + " Blocks", e);
                        } catch (Exception e) {
                            logger.info("Unspecified Open CL error: ", e);
                        } finally {
                            gpuUsage.release();
                        }
                    } else { // verify using java
                        try {
                            Block block = downloadCache.getFirstUnverifiedBlock();
                            Block prevBlock = downloadCache.getBlock(block.getPreviousBlockId());
                            if (prevBlock == null) {
                                prevBlock = blockchain.getBlock(block.getPreviousBlockId());
                            }
                            blockService.preVerify(block, prevBlock);
                            updateAndFireQueueStatus();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (BlockNotAcceptedException e) {
                            logger.error("Block failed to preverify: ", e);
                        }
                    }
                } else {
                    // nothing to verify right now
                    return;
                }
            }
        };
        if (propertyService.getBoolean(Props.GPU_ACCELERATION)) {
            logger.debug("Starting preverifier thread in Open CL mode.");
            threadPool.scheduleThread("VerifyPoc", pocVerificationThread,
                    propertyService.getInt(Props.BLOCK_PROCESS_THREAD_DELAY),
                    TimeUnit.MILLISECONDS);
        } else {
            logger.debug("Starting preverifier thread in CPU mode.");
            threadPool.scheduleThreadCores(pocVerificationThread,
                    propertyService.getInt(Props.BLOCK_PROCESS_THREAD_DELAY),
                    TimeUnit.MILLISECONDS);
        }
    }

    private void updateAndFireQueueStatus() {
        int totalSize = downloadCache.size();
        int unverifiedSize = downloadCache.getUnverifiedSize();
        int verifiedSize = totalSize - unverifiedSize;

        if (totalSize != lastTotalSize || unverifiedSize != lastUnverifiedQueueSize) {
            lastUnverifiedQueueSize = unverifiedSize;
            lastTotalSize = totalSize;
            fireQueueStatusChanged(unverifiedSize, verifiedSize, totalSize);
        }
    }

    private void fireQueueStatusChanged(int downloadCacheUnverifiedSize,
            int downloadCacheVerifiedSize,
            int downloadCacheTotalSize) {
        eventBus.publish(
                new QueueStatusEvent(downloadCacheUnverifiedSize, downloadCacheVerifiedSize, downloadCacheTotalSize));
    }

    private void firePerformanceStatsUpdated(long totalTimeMs, long dbTimeMs, long atTimeMs, int txCount,
            int blockHeight) {
        eventBus.publish(new PerformanceStatsUpdatedEvent(totalTimeMs, dbTimeMs, atTimeMs, txCount, blockHeight));
    }

    private void firePeerCountChanged(int newCount, int newConnectedCount) {
        eventBus.publish(new PeerCountChangedEvent(newCount, newConnectedCount));
    }

    private void updateAndFirePeerCount() {
        int newPeerCount = Peers.getAllPeers().size();
        int newConnectedPeerCount = (int) Peers.getActivePeers().stream()
                .filter(peer -> peer.getState() == Peer.State.CONNECTED)
                .count();
        if (newPeerCount != lastKnownPeerCount || newConnectedPeerCount != lastKnownConnectedPeerCount) {
            lastKnownPeerCount = newPeerCount;
            lastKnownConnectedPeerCount = newConnectedPeerCount;
            firePeerCountChanged(newPeerCount, newConnectedPeerCount);
        }
    }

    private void fireNetVolumeChanged(long uploadedVolume, long downloadedVolume) {
        eventBus.publish(new NetVolumeChangedEvent(uploadedVolume, downloadedVolume));
    }

    private void updateAndFireNetVolume() {
        List<Peer> peersList = Peers.getActivePeers();
        long sumUploadedVolume = 0;
        long sumDownloadedVolume = 0;
        for (Peer peer : peersList) {
            sumUploadedVolume += peer.getUploadedVolume();
            sumDownloadedVolume += peer.getDownloadedVolume();
        }
        fireNetVolumeChanged(sumUploadedVolume, sumDownloadedVolume);
    }

    private void blacklistClean(Block block, Exception e, String description) {
        logger.debug("Blacklisting peer and cleaning cache queue");
        if (block == null) {
            return;
        }
        Peer peer = block.getPeer();
        if (peer != null) {
            peer.blacklist(e, description);
        }
        downloadCache.resetCache();
        updateAndFireQueueStatus();
        logger.debug("Blacklisted peer and cleaned queue");
    }

    private void autoPopOff(int height) {
        if (!autoPopOffEnabled) {
            logger.warn(
                    "Not automatically popping off as it is disabled via properties. If your node becomes stuck you will need to manually pop off.");
            return;
        }
        synchronized (transactionProcessor.getUnconfirmedTransactionsSyncObj()) {
            logger.warn("Auto popping off as failed to push block");
            if (height != autoPopOffLastStuckHeight) {
                autoPopOffLastStuckHeight = height;
                autoPopOffNumberOfBlocks = 0;
            }
            if (autoPopOffNumberOfBlocks == 0) {
                logger.warn("Not popping anything off as this was the first failure at this height");
            } else {
                logger.warn("Popping off {} blocks due to previous failures to push this block",
                        autoPopOffNumberOfBlocks);
                popOffTo(blockchain.getHeight() - autoPopOffNumberOfBlocks);
            }
            autoPopOffNumberOfBlocks++;
        }
    }

    @Override
    public boolean addListener(Listener<Block> listener, BlockchainProcessor.Event eventType) {
        return blockListeners.addListener(listener, eventType);
    }

    @Override
    public boolean removeListener(Listener<Block> listener, Event eventType) {
        return blockListeners.removeListener(listener, eventType);
    }

    @Override
    public Peer getLastBlockchainFeeder() {
        return lastBlockchainFeeder.get();
    }

    @Override
    public int getLastBlockchainFeederHeight() {
        return lastBlockchainFeederHeight.get();
    }

    @Override
    public boolean isScanning() {
        return isScanning.get();
    }

    @Override
    public int getMinRollbackHeight() {
        int trimHeight = (lastTrimHeight.get() > 0
                ? lastTrimHeight.get()
                : Math.max(blockchain.getHeight() - Constants.MAX_ROLLBACK, 0));
        return trimDerivedTables ? trimHeight : 0;
    }

    @Override
    public void processPeerBlock(JsonObject request, Peer peer) throws SignumException {
        Block newBlock = Block.parseBlock(request, blockchain.getHeight());
        // * This process takes care of the blocks that is announced by peers We do not
        // want to be fed forks.
        Block chainblock = downloadCache.getLastBlock();
        if (chainblock != null && chainblock.getId() == newBlock.getPreviousBlockId()) {
            newBlock.setHeight(chainblock.getHeight() + 1);
            newBlock.setByteLength(newBlock.toString().length());
            blockService.calculateBaseTarget(newBlock, chainblock);
            downloadCache.addBlock(newBlock);
            logger.debug(
                    "Peer {} added block from Announce: Id: {} Height: {}",
                    peer.getPeerAddress(),
                    newBlock.getId(), newBlock.getHeight());
        } else {
            logger.debug(
                    "Peer {} sent us block: {} which is not the follow-up block for {}",
                    peer.getPeerAddress(),
                    newBlock.getPreviousBlockId(), chainblock.getId());
        }
    }

    @Override
    public void fullReset() {
        dbCacheManager.flushCache();
        downloadCache.resetCache();
        blockDb.deleteAll(false);
        addGenesisBlock();
        dbCacheManager.flushCache();
        downloadCache.resetCache();
    }

    void setGetMoreBlocks(boolean getMoreBlocks) {
        this.getMoreBlocks.set(getMoreBlocks);
    }

    private void addBlock(Block block) {
        blockchainStore.addBlock(block);
        blockchain.setLastBlock(block);
    }

    private int checkDatabaseState() {
        logger.debug("Block height {}, checking database state...", blockchain.getHeight());
        long totalMined = blockchain.getTotalMined();

        long totalEffectiveBalance = accountService.getAllAccountsBalance();
        for (Escrow escrow : escrowService.getAllEscrowTransactions()) {
            totalEffectiveBalance += escrow.getAmountNQT();
        }

        if (totalMined != totalEffectiveBalance) {
            logger.warn(
                    "Block height {}, total mined {}, total effective+burnt {}",
                    blockchain.getHeight(),
                    totalMined,
                    totalEffectiveBalance);
        }

        isConsistent.set(totalMined == totalEffectiveBalance);

        if (logger.isDebugEnabled()) {
            logger.debug("Total mined {}, total effective {}", totalMined, totalEffectiveBalance);
            logger.debug("Database is consistent: {}", isConsistent.get());
        }
        return Long.compare(totalMined, totalEffectiveBalance);
    }

    private void addGenesisBlock() {
        if (blockDb.hasBlock(genesisBlockId)) {
            logger.info("Genesis block already in database");
            Block lastBlock = blockDb.findLastBlock();
            blockchain.setLastBlock(lastBlock);
            logger.info("Last block height: {}, baseTarget: {}{}", lastBlock.getHeight(),
                    lastBlock.getCapacityBaseTarget(),
                    Signum.getFluxCapacitor().getValue(FluxValues.POC_PLUS)
                            ? ", averageCommitmentNQT " + lastBlock.getAverageCommitment()
                            : "");
            return;
        }
        logger.info("Genesis block not in database, starting from scratch");
        try {
            List<Transaction> transactions = new ArrayList<>();
            MessageDigest digest = Crypto.sha256();
            transactions.forEach(transaction -> digest.update(transaction.getBytes()));
            ByteBuffer bf = ByteBuffer.allocate(0);
            bf.order(ByteOrder.LITTLE_ENDIAN);
            byte[] byteAts = bf.array();
            int genesisTimestamp = Signum.getPropertyService().getInt(Props.GENESIS_TIMESTAMP);
            Block genesisBlock = new Block(
                    -1,
                    genesisTimestamp,
                    0,
                    0,
                    0,
                    0,
                    0,
                    transactions.size() * 128,
                    digest.digest(),
                    Genesis.getCreatorPublicKey(),
                    new byte[32],
                    Genesis.getGenesisBlockSignature(),
                    null,
                    transactions,
                    0, byteAts,
                    -1,
                    Constants.INITIAL_BASE_TARGET);
            blockService.setPrevious(genesisBlock, null);
            addBlock(genesisBlock);
        } catch (SignumException.ValidationException e) {
            logger.info(e.getMessage());
            throw new RuntimeException(e.toString(), e);
        }
    }

    private void pushBlock(final Block block) throws BlockNotAcceptedException {
        long totalStartTime = System.nanoTime();
        long dbStartTime = 0;
        long dbEndTime = 0;

        synchronized (transactionProcessor.getUnconfirmedTransactionsSyncObj()) {
            stores.beginTransaction();
            int curTime = timeService.getEpochTime();

            Block previousLastBlock = null;
            try {

                dbStartTime = System.nanoTime();

                previousLastBlock = blockchain.getLastBlock();

                if (previousLastBlock.getId() != block.getPreviousBlockId()) {
                    throw new BlockOutOfOrderException(
                            "Previous block id doesn't match for block " + block.getHeight()
                                    + ((previousLastBlock.getHeight() + 1) == block.getHeight() ? ""
                                            : " invalid previous height " + previousLastBlock.getHeight()));
                }

                if (block.getVersion() != getBlockVersion()) {
                    throw new BlockNotAcceptedException(
                            "Invalid version " + block.getVersion() + " for block " + block.getHeight());
                }

                if (block.getVersion() != 1
                        && !Arrays.equals(Crypto.sha256().digest(previousLastBlock.getBytes()),
                                block.getPreviousBlockHash())) {
                    throw new BlockNotAcceptedException(
                            "Previous block hash doesn't match for block " + block.getHeight());
                }
                if (block.getTimestamp() > curTime + MAX_TIMESTAMP_DIFFERENCE
                        || block.getTimestamp() <= previousLastBlock.getTimestamp()) {
                    throw new BlockOutOfOrderException("Invalid timestamp: " + block.getTimestamp()
                            + " current time is " + curTime
                            + ", previous block timestamp is " + previousLastBlock.getTimestamp() + ", peer is "
                            + (block.getPeer() != null ? block.getPeer().getAnnouncedAddress() : " null"));
                }
                if (block.getId() == 0L || blockDb.hasBlock(block.getId())) {
                    throw new BlockNotAcceptedException("Duplicate block or invalid id for block " + block.getHeight());
                }
                if (!blockService.verifyGenerationSignature(block)) {
                    throw new BlockNotAcceptedException(
                            "Generation signature verification failed for block " + block.getHeight());
                }
                if (!blockService.verifyBlockSignature(block)) {
                    throw new BlockNotAcceptedException(
                            "Block signature verification failed for block " + block.getHeight());
                }

                final TransactionDuplicatesCheckerImpl transactionDuplicatesChecker = new TransactionDuplicatesCheckerImpl();
                long calculatedTotalAmount = 0;
                long calculatedTotalFee = 0;
                MessageDigest digest = Crypto.sha256();
                List<Transaction> transactions = block.getTransactions();
                long[] feeArray = new long[transactions.size()];
                int slotIdx = 0;

                int maxIndirects = Signum.getPropertyService().getInt(Props.MAX_INDIRECTS_PER_BLOCK);
                int indirectsCount = 0;

                for (Transaction transaction : transactions) {
                    if (transaction.getTimestamp() > curTime + MAX_TIMESTAMP_DIFFERENCE) {
                        throw new BlockOutOfOrderException("Invalid transaction timestamp: "
                                + transaction.getTimestamp() + ", current time is " + curTime);
                    }
                    if (transaction.getTimestamp() > block.getTimestamp() + MAX_TIMESTAMP_DIFFERENCE
                            || transaction.getExpiration() < block.getTimestamp()) {
                        throw new TransactionNotAcceptedException("Invalid transaction timestamp "
                                + transaction.getTimestamp() + " for transaction " + transaction.getStringId()
                                + ", current time is " + curTime + ", block timestamp is " + block.getTimestamp(),
                                transaction);
                    }
                    if (transactionDb.hasTransaction(transaction.getId())) {
                        throw new TransactionNotAcceptedException(
                                "Transaction " + transaction.getStringId() + " is already in the blockchain",
                                transaction);
                    }
                    if (transaction.getReferencedTransactionFullHash() != null && ((!transactionDb.hasTransaction(
                            Convert.fullHashToId(transaction.getReferencedTransactionFullHash())))
                            || (!hasAllReferencedTransactions(transaction, transaction.getTimestamp(), 0)))) {
                        throw new TransactionNotAcceptedException("Missing or invalid referenced transaction "
                                + transaction.getReferencedTransactionFullHash() + " for transaction "
                                + transaction.getStringId(), transaction);
                    }
                    if (transaction.getVersion() != transactionProcessor
                            .getTransactionVersion(previousLastBlock.getHeight())) {
                        throw new TransactionNotAcceptedException("Invalid transaction version "
                                + transaction.getVersion() + " at height " + previousLastBlock.getHeight(),
                                transaction);
                    }

                    if (!transactionService.verifyPublicKey(transaction)) {
                        throw new TransactionNotAcceptedException("Wrong public key in transaction "
                                + transaction.getStringId() + " at height " + previousLastBlock.getHeight(),
                                transaction);
                    }
                    if (Signum.getFluxCapacitor().getValue(FluxValues.AUTOMATED_TRANSACTION_BLOCK)
                            && !economicClustering.verifyFork(transaction)) {
                        if (logger.isDebugEnabled()) {
                            logger.debug(
                                    "Block {} height {} contains transaction that was generated on a fork: {} ecBlockId {} ecBlockHeight {}",
                                    block.getStringId(), previousLastBlock.getHeight() + 1, transaction.getStringId(),
                                    transaction.getEcBlockHeight(), Convert.toUnsignedLong(transaction.getEcBlockId()));
                        }
                        throw new TransactionNotAcceptedException("Transaction belongs to a different fork",
                                transaction);
                    }
                    if (transaction.getId() == 0L) {
                        throw new TransactionNotAcceptedException("Invalid transaction id", transaction);
                    }

                    if (transactionDuplicatesChecker.hasAnyDuplicate(transaction)) {
                        throw new TransactionNotAcceptedException(
                                "Transaction is a duplicate: " + transaction.getStringId(), transaction);
                    }

                    int txIndirects = transaction.getType().getIndirectIncomings(transaction).size();
                    if (indirectsCount + txIndirects > maxIndirects) {
                        throw new TransactionNotAcceptedException(
                                "Maximum indirects limit of " + maxIndirects + " reached: " + transaction.getStringId(),
                                transaction);
                    }
                    indirectsCount += txIndirects;

                    try {
                        transactionService.validate(transaction);
                    } catch (SignumException.ValidationException e) {
                        throw new TransactionNotAcceptedException(e.getMessage(), transaction);
                    }

                    calculatedTotalAmount += transaction.getAmountNqt();
                    calculatedTotalFee += transaction.getFeeNqt();
                    digest.update(transaction.getBytes());
                    indirectIncomingService.processTransaction(transaction);
                    feeArray[slotIdx] = transaction.getFeeNqt();
                    slotIdx += 1;
                }

                if (calculatedTotalAmount > block.getTotalAmountNqt()
                        || calculatedTotalFee > block.getTotalFeeNqt()) {
                    throw new BlockNotAcceptedException(
                            "Total amount or fee don't match transaction totals for block " + block.getHeight());
                }

                if (Signum.getFluxCapacitor().getValue(FluxValues.SODIUM)
                        && !Signum.getFluxCapacitor().getValue(FluxValues.SPEEDWAY)) {
                    Arrays.sort(feeArray);
                    for (int i = 0; i < feeArray.length; i++) {
                        if (feeArray[i] < Constants.FEE_QUANT_SIP3 * (i + 1)) {
                            throw new BlockNotAcceptedException(
                                    "Transaction fee is not enough to be included in this block " + block.getHeight());
                        }
                    }
                }

                if (!Arrays.equals(digest.digest(), block.getPayloadHash())) {
                    throw new BlockNotAcceptedException("Payload hash doesn't match for block " + block.getHeight());
                }

                blockService.setPrevious(block, previousLastBlock);
                blockListeners.notify(block, Event.BEFORE_BLOCK_ACCEPT);
                transactionProcessor.removeForgedTransactions(transactions);
                transactionProcessor.requeueAllUnconfirmedTransactions();
                accountService.flushAccountTable();
                addBlock(block);

                long remainingAmount = Convert.safeSubtract(block.getTotalAmountNqt(), calculatedTotalAmount);
                long remainingFee = Convert.safeSubtract(block.getTotalFeeNqt(), calculatedTotalFee);

                dbEndTime = System.nanoTime();
                accept(block, remainingAmount, remainingFee);
                derivedTableManager.getDerivedTables().forEach(DerivedTable::finish);
                stores.commitTransaction();
                // We make sure downloadCache do not have this block anymore, but only after all
                // DBs have it
                downloadCache.removeBlock(block);
                if (trimDerivedTables && (block.getHeight() % Constants.MAX_ROLLBACK) == 0) {
                    if (checkDatabaseState() == 0) {
                        // Only trim a consistent database, otherwise it would be impossible to fix it
                        // by roll back
                        lastTrimHeight.set(Math.max(block.getHeight() - Constants.MAX_ROLLBACK, 0));
                    } else {
                        lastTrimHeight.set(0);
                        logger.error("Balance mismatch on the database, please try popping off to block {}",
                                getMinRollbackHeight());
                    }
                }
            } catch (BlockNotAcceptedException | ArithmeticException e) {
                stores.rollbackTransaction();
                blockchain.setLastBlock(previousLastBlock);
                downloadCache.resetCache();
                atProcessorCache.reset();
                throw e;
            } finally {
                stores.endTransaction();
            }

            long totalEndTime = System.nanoTime();
            long totalTimeMs = TimeUnit.NANOSECONDS.toMillis(totalEndTime - totalStartTime);
            long dbTimeMs = dbStartTime > 0 ? TimeUnit.NANOSECONDS.toMillis(dbEndTime - dbStartTime) : 0;
            atTimeMs = TimeUnit.NANOSECONDS.toMillis(atTimeNanos);
            firePerformanceStatsUpdated(totalTimeMs, dbTimeMs, atTimeMs, block.getTransactions().size(),
                    block.getHeight());

            logger.debug("Successfully pushed {} (height {})", block.getId(), block.getHeight());
            statisticsManager.blockAdded();
            blockListeners.notify(block, Event.BLOCK_PUSHED);
            if (block.getTimestamp() >= timeService.getEpochTime() - MAX_TIMESTAMP_DIFFERENCE) {
                Peers.sendToSomePeers(block);
            }
            if (block.getHeight() >= autoPopOffLastStuckHeight) {
                autoPopOffNumberOfBlocks = 0;
            }
        }
    }

    private void accept(Block block, Long remainingAmount, Long remainingFee)
            throws BlockNotAcceptedException {
        subscriptionService.clearRemovals();
        transactionService.startNewBlock();
        for (Transaction transaction : block.getTransactions()) {
            if (!transactionService.applyUnconfirmed(transaction)) {
                throw new TransactionNotAcceptedException(
                        "Transaction not accepted: " + transaction.getStringId(), transaction);
            }
        }

        // ATs
        AtBlock atBlock;
        AT.clearPending(block.getHeight(), block.getGeneratorId());
        long atStartTime = 0;
        long atEndTime = 0;
        try {
            atStartTime = System.nanoTime();
            atBlock = AtController.validateATs(block.getBlockAts(), blockchain.getHeight(), block.getGeneratorId());
            atEndTime = System.nanoTime();
        } catch (AtException e) {
            throw new BlockNotAcceptedException(
                    "ats are not matching at block height " + blockchain.getHeight() + " (" + e + ")");
        }

        long calculatedRemainingAmount = 0;
        long calculatedRemainingFee = 0;
        calculatedRemainingAmount += atBlock.getTotalAmount();
        calculatedRemainingFee += atBlock.getTotalFees();
        // ATs
        if (subscriptionService.isEnabled()) {
            calculatedRemainingFee += subscriptionService.applyUnconfirmed(block.getTimestamp(), block.getHeight());
        }
        if (remainingAmount != null && remainingAmount != calculatedRemainingAmount) {
            throw new BlockNotAcceptedException(
                    "Calculated remaining amount doesn't add up for block " + block.getHeight());
        }
        if (remainingFee != null && remainingFee != calculatedRemainingFee) {
            throw new BlockNotAcceptedException(
                    "Calculated remaining fee doesn't add up for block " + block.getHeight());
        }
        blockListeners.notify(block, Event.BEFORE_BLOCK_APPLY);
        blockService.apply(block);
        subscriptionService.applyConfirmed(block, blockchain.getHeight());
        if (escrowService.isEnabled()) {
            escrowService.updateOnBlock(block, blockchain.getHeight());
        }
        blockListeners.notify(block, Event.AFTER_BLOCK_APPLY);
        if (!block.getTransactions().isEmpty()) {
            transactionProcessor.notifyListeners(block.getTransactions(),
                    TransactionProcessor.Event.ADDED_CONFIRMED_TRANSACTIONS);
        }
        atTimeNanos = atEndTime > 0 ? atEndTime - atStartTime : 0;
    }

    @Override
    public List<Block> popOffTo(int height) {
        List<Block> blocks = popOffTo(blockchain.getBlockAtHeight(height), null);
        if (Boolean.FALSE.equals(propertyService.getBoolean(Props.DB_SKIP_CHECK))
                && checkDatabaseState() != 0) {
            logger.warn(
                    "Database is inconsistent,"
                            + " try to pop off to block height {} or sync from empty.",
                    getMinRollbackHeight());
        }
        return blocks;
    }

    private List<Block> popOffTo(Block commonBlock, List<Block> forkBlocks) {

        int currentHeight = blockchain.getHeight();
        if (commonBlock.getHeight() < getMinRollbackHeight()) {
            throw new IllegalArgumentException("Rollback to height " + commonBlock.getHeight() + " not suppported, "
                    + "current height " + currentHeight);
        }
        if (!blockchain.hasBlock(commonBlock.getId())) {
            logger.debug("Block {} not found in blockchain, nothing to pop off", commonBlock.getStringId());
            return Collections.emptyList();
        }
        if (commonBlock.getHeight() > currentHeight) {
            logger.warn("Rollback from {} to {} is non-sense, ignoring pop off", currentHeight,
                    commonBlock.getHeight());
            return Collections.emptyList();

        }
        List<Block> poppedOffBlocks = new ArrayList<>();
        synchronized (downloadCache) {
            synchronized (transactionProcessor.getUnconfirmedTransactionsSyncObj()) {
                try {
                    stores.beginTransaction();
                    Block block = blockchain.getLastBlock();
                    logger.info("Rollback from {} to {}", block.getHeight(), commonBlock.getHeight());
                    while (block.getId() != commonBlock.getId() && block.getId() != genesisBlockId) {
                        if (forkBlocks != null) {
                            for (Block fb : forkBlocks) {
                                if (fb.getHeight() == block.getHeight()
                                        && fb.getGeneratorId() == block.getGeneratorId()) {
                                    logger.info("Possible rewrite fork, ID {} block being monitored {}",
                                            block.getGeneratorId(), block.getHeight());
                                    blockService.watchBlock(block);
                                }
                            }
                        }
                        logger.debug("Popping block {} generator {} sig {}", block.getHeight(),
                                SignumID.fromLong(block.getGeneratorId()).getID(),
                                Hex.toHexString(block.getBlockSignature()));
                        logger.debug("Block timestamp {} base target {} difficulty {} commitment {}",
                                block.getTimestamp(), block.getBaseTarget(),
                                block.getCumulativeDifficulty(), block.getCommitment());
                        poppedOffBlocks.add(block);
                        block = popLastBlock();
                    }
                    logger.debug("Rolling back derived tables...");
                    for (DerivedTable table : derivedTableManager.getDerivedTables()) {
                        logger.debug("Rolling back {}", table.getTable());
                        table.rollback(commonBlock.getHeight());
                    }
                    indirectIncomingService.rollback(commonBlock.getHeight());
                    dbCacheManager.flushCache();
                    stores.commitTransaction();
                    downloadCache.resetCache();
                    atProcessorCache.reset();
                    ;
                } catch (RuntimeException e) {
                    stores.rollbackTransaction();
                    logger.debug("Error popping off to {}", commonBlock.getHeight(), e);
                    throw e;
                } finally {
                    stores.endTransaction();
                }
            }
        }
        return poppedOffBlocks;
    }

    private Block popLastBlock() {
        Block block = blockchain.getLastBlock();
        if (block.getId() == genesisBlockId) {
            throw new RuntimeException("Cannot pop off genesis block");
        }
        Block previousBlock = blockDb.findBlock(block.getPreviousBlockId());
        List<Transaction> txs = block.getTransactions();
        blockchain.setLastBlock(block, previousBlock);
        txs.forEach(Transaction::unsetBlock);
        blockDb.deleteBlocksFrom(block.getId());
        blockListeners.notify(block, Event.BLOCK_POPPED);
        return previousBlock;
    }

    private int getBlockVersion() {
        return Signum.getFluxCapacitor().getValue(FluxValues.SMART_FEES) ? 4 : 3;
    }

    private boolean preCheckUnconfirmedTransaction(TransactionDuplicatesCheckerImpl transactionDuplicatesChecker,
            UnconfirmedTransactionStore unconfirmedTransactionStore, Transaction transaction) {
        boolean ok = !transactionDuplicatesChecker.hasAnyDuplicate(transaction)
                && !transactionDb.hasTransaction(transaction.getId());
        if (!ok) {
            unconfirmedTransactionStore.remove(transaction);
        } else {
            ok = hasAllReferencedTransactions(transaction, transaction.getTimestamp(), 0);
        }
        return ok;
    }

    @Override
    public void generateBlock(String secretPhrase, byte[] publicKey, Long nonce) throws BlockNotAcceptedException {
        synchronized (downloadCache) {
            if (!isConsistent.get()) {
                logger.warn("block generation with an inconsistent database, might make you to mine alone in a fork");
            }
            downloadCache.lockCache(); // stop all incoming blocks.
            UnconfirmedTransactionStore unconfirmedTransactionStore = stores.getUnconfirmedTransactionStore();
            SortedSet<Transaction> orderedBlockTransactions = new TreeSet<>();

            int blockSize = Signum.getFluxCapacitor().getValue(FluxValues.MAX_NUMBER_TRANSACTIONS);
            int payloadSize = Signum.getFluxCapacitor().getValue(FluxValues.MAX_PAYLOAD_LENGTH);

            long totalAmountNqt = 0;
            long totalFeeNqt = 0;
            long totalFeeCashBackNqt = 0;
            long totalFeeBurntNqt = 0;
            int indirectsCount = 0;

            /**
             * This map is used to track the provisional unconfirmed balances of accounts
             * during block generation. It allows us to simulate the state of accounts
             * without modifying the actual unconfirmed balances in the database,
             * thus preventing double-spending within the same block.
             */
            final Map<Long, Long> provisionalUnconfirmedBalances = new HashMap<>();

            final Block previousBlock = blockchain.getLastBlock();
            final int blockTimestamp = timeService.getEpochTime();
            final int blockHeight = previousBlock.getHeight() + 1;

            // this is just an validation. which collects all valid transactions, which fit
            // into the block
            // finally all stuff is reverted so nothing is written to the db
            // the block itself with all transactions we found is pushed using pushBlock
            // which calls
            // accept (so it's going the same way like a received/synced block)
            try {
                stores.beginTransaction();

                final TransactionDuplicatesCheckerImpl transactionDuplicatesChecker = new TransactionDuplicatesCheckerImpl();

                ToLongFunction<Transaction> priorityCalculator = transaction -> {
                    int age = blockTimestamp + 1 - transaction.getTimestamp();
                    if (age < 0) {
                        age = 1;
                    }

                    long feePriority = transaction.getFeeNqt()
                            * (transaction.getSize() / Constants.ORDINARY_TRANSACTION_BYTES);
                    // So the age has less priority (60 minutes to increase the priority to the next
                    // level)
                    // TODO: consider giving priority based on the last sent transaction and not
                    // transaction age to improve spam protection
                    long priority = (feePriority * 60) + Signum.getFluxCapacitor().getValue(FluxValues.FEE_QUANT) * age;

                    return priority;
                };

                // Map of slot number -> transaction
                Map<Long, Transaction> transactionsToBeIncluded;
                Stream<Transaction> inclusionCandidates = unconfirmedTransactionStore.getAll().stream()
                        .filter(transaction -> // Normal filtering
                        transaction.getVersion() == transactionProcessor
                                .getTransactionVersion(previousBlock.getHeight())
                                && transaction.getExpiration() >= blockTimestamp
                                && transaction.getTimestamp() <= blockTimestamp + MAX_TIMESTAMP_DIFFERENCE
                                && (!Signum.getFluxCapacitor().getValue(FluxValues.AUTOMATED_TRANSACTION_BLOCK)
                                        || economicClustering.verifyFork(transaction)))
                        // ↓ Extra check for transactions that are to be considered
                        .filter(transaction -> preCheckUnconfirmedTransaction(transactionDuplicatesChecker,
                                unconfirmedTransactionStore, transaction));

                if (Signum.getFluxCapacitor().getValue(FluxValues.PRE_POC2)
                        && !Signum.getFluxCapacitor().getValue(FluxValues.SPEEDWAY)) {
                    // In this step we get all unconfirmed transactions and then sort them by slot,
                    // followed by priority
                    Map<Long, TreeMap<Long, Transaction>> unconfirmedTransactionsOrderedBySlotThenPriority = new HashMap<>();
                    inclusionCandidates.collect(Collectors.toMap(Function.identity(), priorityCalculator::applyAsLong))
                            .forEach((transaction, priority) -> {
                                long slot = (transaction.getFeeNqt() - (transaction.getFeeNqt() % FEE_QUANT_SIP3))
                                        / FEE_QUANT_SIP3;
                                slot = Math.min(Signum.getFluxCapacitor().getValue(FluxValues.MAX_NUMBER_TRANSACTIONS),
                                        slot);
                                TreeMap<Long, Transaction> utxInSlot = unconfirmedTransactionsOrderedBySlotThenPriority
                                        .get(slot);
                                if (utxInSlot == null) {
                                    // Use a tree map in reverse order so we automatically get a descending priority
                                    // list
                                    utxInSlot = new TreeMap<>(Collections.reverseOrder());
                                    unconfirmedTransactionsOrderedBySlotThenPriority.put(slot, utxInSlot);
                                }
                                // if we already have this identical priority, make sure they are unique
                                while (utxInSlot.get(priority) != null) {
                                    priority--;
                                }
                                utxInSlot.put(priority, transaction);
                            });

                    // Fill the unconfirmed transactions to be included from top to bottom
                    Map<Long, Transaction> slotTransactionsToBeincluded = new HashMap<>();
                    int maxSlot = Signum.getFluxCapacitor().getValue(FluxValues.MAX_NUMBER_TRANSACTIONS);
                    for (long slot = maxSlot; slot >= 1; slot--) {
                        boolean slotFilled = false;
                        for (long slotUnconfirmed = maxSlot; slotUnconfirmed >= slot; slotUnconfirmed--) {
                            // using a tree map we already have it naturally sorted by priority
                            TreeMap<Long, Transaction> candidateTxs = unconfirmedTransactionsOrderedBySlotThenPriority
                                    .get(slotUnconfirmed);
                            if (candidateTxs != null) {
                                Iterator<Transaction> itTx = candidateTxs.values().iterator();
                                while (itTx.hasNext()) {
                                    Transaction tx = itTx.next();

                                    slotTransactionsToBeincluded.put(slot, tx);
                                    itTx.remove();
                                    slotFilled = true;
                                    break;
                                }
                                if (slotFilled) {
                                    break;
                                }
                            }
                        }
                    }
                    transactionsToBeIncluded = slotTransactionsToBeincluded;
                } else {
                    // Just confirm transactions by the highest priority
                    Stream<Transaction> transactionsOrderedByPriority = inclusionCandidates
                            .sorted(new Comparator<Transaction>() {
                                @Override
                                public int compare(Transaction t1, Transaction t2) {
                                    return Long.compare(priorityCalculator.applyAsLong(t2),
                                            priorityCalculator.applyAsLong(t1));
                                }
                            });
                    Map<Long, Transaction> transactionsOrderedBySlot = new HashMap<>();
                    AtomicLong currentSlot = new AtomicLong(1);
                    transactionsOrderedByPriority
                            .forEach(tx -> { // This should do highest priority to lowest priority
                                transactionsOrderedBySlot.put(currentSlot.get(), tx);
                                currentSlot.incrementAndGet();
                            });
                    transactionsToBeIncluded = transactionsOrderedBySlot;
                }

                int maxIndirects = Signum.getPropertyService().getInt(Props.MAX_INDIRECTS_PER_BLOCK);
                long feeQuant = Signum.getFluxCapacitor().getValue(FluxValues.FEE_QUANT);
                transactionService.startNewBlock();
                for (Map.Entry<Long, Transaction> entry : transactionsToBeIncluded.entrySet()) {
                    Transaction transaction = entry.getValue();

                    if (blockSize <= 0 || payloadSize <= 0) {
                        break;
                    } else if (transaction.getSize() > payloadSize) {
                        continue;
                    }

                    int txIndirects = transaction.getType().getIndirectIncomings(transaction).size();
                    if (indirectsCount + txIndirects > maxIndirects) {
                        // skip this transaction, max indirects per block reached
                        continue;
                    }
                    indirectsCount += txIndirects;

                    long slot = entry.getKey();
                    long slotFee = Signum.getFluxCapacitor().getValue(FluxValues.PRE_POC2) ? slot * FEE_QUANT_SIP3
                            : ONE_SIGNA;
                    if (Signum.getFluxCapacitor().getValue(FluxValues.SPEEDWAY)) {
                        // we already got the list by priority, no need to check the fees again
                        slotFee = feeQuant;
                    }
                    if (transaction.getFeeNqt() >= slotFee) {
                        if (transactionService.applyUnconfirmed(transaction)) {
                            try {
                                transactionService.validate(transaction);
                                payloadSize -= transaction.getSize();
                                totalAmountNqt += transaction.getAmountNqt();
                                totalFeeNqt += transaction.getFeeNqt();
                                if (Signum.getFluxCapacitor().getValue(FluxValues.SMART_FEES, blockHeight)) {
                                    totalFeeCashBackNqt += transaction.getFeeNqt()
                                            / propertyService.getInt(Props.CASH_BACK_FACTOR);
                                }
                                orderedBlockTransactions.add(transaction);
                                blockSize--;
                            } catch (SignumException.NotCurrentlyValidException e) {
                                transactionService.undoUnconfirmed(transaction);
                            } catch (SignumException.ValidationException e) {
                                unconfirmedTransactionStore.remove(transaction);
                                transactionService.undoUnconfirmed(transaction);
                            }
                        } else {
                            // Drop duplicates and transactions that cannot be applied
                            unconfirmedTransactionStore.remove(transaction);
                        }
                    }
                }

                if (subscriptionService.isEnabled()) {
                    subscriptionService.clearRemovals();
                    long subscriptionFeeNqt = subscriptionService.calculateFees(blockTimestamp, blockHeight);
                    totalFeeNqt += subscriptionFeeNqt;
                    if (Signum.getFluxCapacitor().getValue(FluxValues.SMART_FEES, blockHeight)) {
                        totalFeeBurntNqt += subscriptionFeeNqt;
                    }
                }
            } catch (Exception e) {
                stores.rollbackTransaction();
                throw e;
            } finally {
                stores.rollbackTransaction();
                stores.endTransaction();
            }

            // ATs for block
            long generatorId = Account.getId(publicKey);
            AT.clearPending(blockHeight, generatorId);
            AtBlock atBlock = AtController.getCurrentBlockATs(payloadSize, blockHeight, generatorId, indirectsCount);
            byte[] byteAts = atBlock.getBytesForBlock();

            // digesting AT Bytes
            if (byteAts != null) {
                payloadSize -= byteAts.length;
                totalFeeNqt += atBlock.getTotalFees();
                if (Signum.getFluxCapacitor().getValue(FluxValues.SMART_FEES, blockHeight)) {
                    totalFeeBurntNqt += atBlock.getTotalFees();
                }
                totalAmountNqt += atBlock.getTotalAmount();
            }

            // ATs for block

            MessageDigest digest = Crypto.sha256();
            orderedBlockTransactions.forEach(transaction -> digest.update(transaction.getBytes()));
            byte[] payloadHash = digest.digest();
            byte[] generationSignature = generator.calculateGenerationSignature(
                    previousBlock.getGenerationSignature(), previousBlock.getGeneratorId());
            Block block;
            byte[] previousBlockHash = Crypto.sha256().digest(previousBlock.getBytes());
            try {
                block = new Block(getBlockVersion(), blockTimestamp,
                        previousBlock.getId(), totalAmountNqt, totalFeeNqt, totalFeeCashBackNqt, totalFeeBurntNqt,
                        Signum.getFluxCapacitor().getValue(FluxValues.MAX_PAYLOAD_LENGTH) - payloadSize, payloadHash,
                        publicKey,
                        generationSignature, null, previousBlockHash, new ArrayList<>(orderedBlockTransactions), nonce,
                        byteAts, previousBlock.getHeight(), Constants.INITIAL_BASE_TARGET);
            } catch (SignumException.ValidationException e) {
                // shouldn't happen because all transactions are already validated
                logger.info("Error generating block", e);
                return;
            }
            block.sign(secretPhrase);
            blockService.setPrevious(block, previousBlock);
            try {
                blockService.preVerify(block, previousBlock);
                pushBlock(block);
                blockListeners.notify(block, Event.BLOCK_GENERATED);
                if (logger.isDebugEnabled()) {
                    logger.debug("Account {} generated block {} at height {}",
                            Convert.toUnsignedLong(block.getGeneratorId()), block.getStringId(), block.getHeight());
                }
                downloadCache.resetCache();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (TransactionNotAcceptedException e) {
                logger.debug("Generate block failed: {}", e.getMessage());
                Transaction transaction = e.getTransaction();
                logger.debug("Removing invalid transaction: {}", transaction.getStringId());
                unconfirmedTransactionStore.remove(transaction);
                throw e;
            } catch (BlockNotAcceptedException e) {
                logger.debug("Generate block failed: {}", e.getMessage());
                throw e;
            }
        } // end synchronized cache
    }

    private boolean hasAllReferencedTransactions(Transaction transaction, int timestamp, int count) {
        // TODO: consider cleaning this method after the upgrade.
        if (transaction.getReferencedTransactionFullHash() == null) {
            if (Signum.getFluxCapacitor().getValue(FluxValues.SPEEDWAY)) {
                return true;
            }
            return timestamp - transaction.getTimestamp() < 60 * 1440 * 60 && count < 10;
        }
        transaction = transactionDb.findTransactionByFullHash(transaction.getReferencedTransactionFullHash());
        if (!subscriptionService.isEnabled() && transaction != null && transaction.getSignature() == null) {
            transaction = null;
        }
        if (Signum.getFluxCapacitor().getValue(FluxValues.SPEEDWAY)) {
            // No need to go deeper checking, if it is on the DB and confirmed already
            return transaction != null;
        }
        return transaction != null && hasAllReferencedTransactions(transaction, timestamp, count + 1);
    }
}
