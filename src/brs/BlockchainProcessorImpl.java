package brs;

import brs.at.AT;
import brs.at.AtBlock;
import brs.at.AtController;
import brs.at.AtException;
import brs.crypto.Crypto;
import brs.db.BlockDb;
import brs.db.DerivedTable;
import brs.db.TransactionDb;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.store.BlockchainStore;
import brs.db.store.DerivedTableManager;
import brs.db.store.Stores;
import brs.fluxcapacitor.FluxValues;
import brs.peer.Peer;
import brs.peer.Peers;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.*;
import brs.statistics.StatisticsManagerImpl;
import brs.transactionduplicates.TransactionDuplicatesCheckerImpl;
import brs.unconfirmedtransactions.UnconfirmedTransactionStore;
import brs.util.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.*;
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

import static brs.Constants.FEE_QUANT;
import static brs.Constants.ONE_BURST;

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

  private final boolean autoPopOffEnabled;
  private int autoPopOffLastStuckHeight = 0;
  private int autoPopOffNumberOfBlocks = 0;

  public final void setOclVerify(Boolean b) {
    oclVerify = b;
  }

  public final Boolean getOclVerify() {
    return oclVerify;
  }

  public BlockchainProcessorImpl(ThreadPool threadPool, BlockService blockService, TransactionProcessorImpl transactionProcessor, BlockchainImpl blockchain,
                                 PropertyService propertyService,
                                 SubscriptionService subscriptionService, TimeService timeService, DerivedTableManager derivedTableManager,
                                 BlockDb blockDb, TransactionDb transactionDb, EconomicClustering economicClustering, BlockchainStore blockchainStore, Stores stores, EscrowService escrowService,
                                 TransactionService transactionService, DownloadCacheImpl downloadCache, Generator generator, StatisticsManagerImpl statisticsManager, DBCacheManagerImpl dbCacheManager,
                                 AccountService accountService, IndirectIncomingService indirectIncomingService) {
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

    autoPopOffEnabled = propertyService.getBoolean(Props.AUTO_POP_OFF_ENABLED);

    oclVerify = propertyService.getBoolean(Props.GPU_ACCELERATION); // use GPU acceleration ?
    oclUnverifiedQueue = propertyService.getInt(Props.GPU_UNVERIFIED_QUEUE);

    trimDerivedTables = propertyService.getBoolean(Props.DB_TRIM_DERIVED_TABLES);

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

    blockListeners.addListener(block -> transactionProcessor.revalidateUnconfirmedTransactions(), Event.BLOCK_PUSHED);

    if (trimDerivedTables) {
      blockListeners.addListener(block -> {
        if (block.getHeight() % 1440 == 0) {
          lastTrimHeight.set(Math.max(block.getHeight() - Constants.MAX_ROLLBACK, 0));
          if (lastTrimHeight.get() > 0) {
            this.derivedTableManager.getDerivedTables().forEach(table -> table.trim(lastTrimHeight.get()));
          }
        }
      }, Event.AFTER_BLOCK_APPLY);
    }

    addGenesisBlock();

    Runnable getMoreBlocksThread = new Runnable() {
      private JsonElement getCumulativeDifficultyRequest;

      private boolean peerHasMore;

      @Override
      public void run() {
        if (propertyService.getBoolean(Props.DEV_OFFLINE)) return;
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
              // unlocking cache for writing.
              // This must be done before we query where to add blocks.
              // We sync the cache in event of pop off
              synchronized (BlockchainProcessorImpl.this.downloadCache) {
                downloadCache.unlockCache();
              }


              if (downloadCache.isFull()) {
                return;
              }
              peerHasMore = true;
              Peer peer = Peers.getAnyPeer(Peer.State.CONNECTED);
              if (peer == null) {
                logger.debug("No peer connected.");
                return;
              }
              JsonObject response = peer.send(getCumulativeDifficultyRequest);
              if (response == null) {
                return;
              }
              if (response.get("blockchainHeight") != null) {
                lastBlockchainFeeder.set(peer);
                lastBlockchainFeederHeight.set(JSON.getAsInt(response.get("blockchainHeight")));
              } else {
                logger.debug("Peer has no chainheight");
                return;
              }

              /* Cache now contains Cumulative Difficulty */

              BigInteger curCumulativeDifficulty = downloadCache.getCumulativeDifficulty();
              String peerCumulativeDifficulty = JSON.getAsString(response.get("cumulativeDifficulty"));
              if (peerCumulativeDifficulty == null) {
                logger.debug("Peer CumulativeDifficulty is null");
                return;
              }
              BigInteger betterCumulativeDifficulty = new BigInteger(peerCumulativeDifficulty);
              if (betterCumulativeDifficulty.compareTo(curCumulativeDifficulty) <= 0) {
                return;
              }

              long commonBlockId = Genesis.GENESIS_BLOCK_ID;
              long cacheLastBlockId = downloadCache.getLastBlockId();

              // Now we will find the highest common block between ourself and our peer
              if (cacheLastBlockId != Genesis.GENESIS_BLOCK_ID) {
                commonBlockId = getCommonMilestoneBlockId(peer);
                if (commonBlockId == 0 || !peerHasMore) {
                  logger.debug("We could not get a common milestone block from peer.");
                  return;
                }
              }

              /*
               * if we did not get the last block in chain as common block we will be downloading a
               * fork. however if it is to far off we cannot process it anyway. canBeFork will check
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
                  logger.warn("Our peer want to feed us a fork that is more than "
                          + Constants.MAX_ROLLBACK + " blocks old.");
                  return;
                }
              }

              JsonArray nextBlocks = getNextBlocks(peer, commonBlockId);
              if (nextBlocks == null || nextBlocks.size() == 0) {
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
                  block = Block.parseBlock(blockData, height);
                  // Make sure it maps back to chain
                  if (lastBlock.getId() != block.getPreviousBlockId()) {
                    logger.debug("Discarding downloaded data. Last downloaded blocks is rubbish");
                    logger.debug("DB blockID: {} DB blockheight: {} Downloaded previd: {}", lastBlock.getId(), lastBlock.getHeight(), block.getPreviousBlockId());
                    return;
                  }
                  // set height and cumulative difficulty to block
                  block.setHeight(height);
                  block.setPeer(peer);
                  block.setByteLength(JSON.toJsonString(blockData).length());
                  blockService.calculateBaseTarget(block, lastBlock);
                  if (saveInCache) {
                    if (downloadCache.getLastBlockId() == block.getPreviousBlockId()) { //still maps back? we might have got announced/forged blocks
                      if (!downloadCache.addBlock(block)) {
                        //we stop the loop since cahce has been locked
                        return;
                      }
                      if (logger.isDebugEnabled()) {
                          logger.debug("Added from download: Id: {} Height: {}", block.getId(), block.getHeight());
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
                } catch (RuntimeException | BurstException.ValidationException e) {
                  logger.info("Failed to parse block: {}" + e.toString(), e);
                  logger.info("Failed to parse block trace: {}", Arrays.toString(e.getStackTrace()));
                  peer.blacklist(e, "pulled invalid data using getCumulativeDifficulty");
                  return;
                } catch (Exception e) {
                  logger.warn("Unhandled exception {}" + e.toString(), e);
                  logger.warn("Unhandled exception trace: {}", Arrays.toString(e.getStackTrace()));
                }
                //executor shutdown?
                if (Thread.currentThread().isInterrupted())
                  return;
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
                  peer.blacklist("peer claimed to have bigger cumulative difficulty but in reality it did not.");
                  downloadCache.resetForkBlocks();
                  break;
                }
                processFork(peer, downloadCache.getForkList(), commonBlockId);
              }

            } catch (BurstException.StopException e) {
              logger.info("Blockchain download stopped: {}", e.getMessage());
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
            return Genesis.GENESIS_BLOCK_ID;
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
          logger.debug("Getting next Blocks after {} from {}", curBlockId, peer.getPeerAddress());
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
        logger.warn("A fork is detected. Waiting for cache to be processed.");
        downloadCache.lockCache(); //dont let anything add to cache!
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
            logger.warn("Cache is now processed. Starting to process fork.");
            Block forkBlock = blockchain.getBlock(forkBlockId);

            // we read the current cumulative difficulty
            BigInteger curCumulativeDifficulty = blockchain.getLastBlock().getCumulativeDifficulty();

            // We remove blocks from chain back to where we start our fork
            // and save it in a list if we need to restore
            List<Block> myPoppedOffBlocks = popOffTo(forkBlock);

            // now we check that our chain is popped off.
            // If all seems ok is we try to push fork.
            int pushedForkBlocks = 0;
            if (blockchain.getLastBlock().getId() == forkBlockId) {
              for (Block block : forkBlocks) {
                if (blockchain.getLastBlock().getId() == block.getPreviousBlockId()) {
                  try {
                    blockService.preVerify(block);
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
             * we check if we succeeded to push any block. if we did we check against cumulative
             * difficulty If it is lower we blacklist peer and set chain to be processed later.
             */
            if (pushedForkBlocks > 0 && blockchain.getLastBlock().getCumulativeDifficulty()
                    .compareTo(curCumulativeDifficulty) < 0) {
              logger.warn("Fork was bad and pop off was caused by peer {}, blacklisting", peer.getPeerAddress());
              peer.blacklist("got a bad fork");
              List<Block> peerPoppedOffBlocks = popOffTo(forkBlock);
              pushedForkBlocks = 0;
              peerPoppedOffBlocks.forEach(block -> transactionProcessor.processLater(block.getTransactions()));
            }

            // if we did not push any blocks we try to restore chain.
            if (pushedForkBlocks == 0) {
              for (int i = myPoppedOffBlocks.size() - 1; i >= 0; i--) {
                Block block = myPoppedOffBlocks.remove(i);
                try {
                  blockService.preVerify(block);
                  pushBlock(block);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } catch (BlockNotAcceptedException e) {
                  logger.warn("Popped off block no longer acceptable: " + JSON.toJsonString(block.getJsonObject()), e);
                  break;
                }
              }
            } else {
              myPoppedOffBlocks.forEach(block -> transactionProcessor.processLater(block.getTransactions()));
              logger.warn("Successfully switched to better chain.");
            }
            logger.warn("Forkprocessing complete.");
            downloadCache.resetForkBlocks();
            downloadCache.resetCache(); // Reset and set cached vars to chaindata.
          }
        }
      }
    };
    threadPool.scheduleThread("GetMoreBlocks", getMoreBlocksThread, Constants.BLOCK_PROCESS_THREAD_DELAY, TimeUnit.MILLISECONDS);
    /* this should fetch first block in cache */
    //resetting cache because we have blocks that cannot be processed.
    //pushblock removes the block from cache.
    Runnable blockImporterThread = () -> {
      while (!Thread.interrupted() && ThreadPool.running.get() && downloadCache.size() > 0) {
        try {
          Block lastBlock = blockchain.getLastBlock();
          Long lastId = lastBlock.getId();
          Block currentBlock = downloadCache.getNextBlock(lastId); /* this should fetch first block in cache */
          if (currentBlock == null || currentBlock.getHeight() != (lastBlock.getHeight() + 1)) {
            if (logger.isDebugEnabled()) {
              logger.debug("cache is reset due to orphaned block(s). CacheSize: {}", downloadCache.size());
            }
            downloadCache.resetCache(); //resetting cache because we have blocks that cannot be processed.
            break;
          }
          try {
            if (!currentBlock.isVerified()) {
              downloadCache.removeUnverified(currentBlock.getId());
              blockService.preVerify(currentBlock);
              logger.debug("block was not preverified");
            }
            pushBlock(currentBlock); //pushblock removes the block from cache.
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (BlockNotAcceptedException e) {
            logger.error("Block not accepted", e);
            blacklistClean(currentBlock, e, "found invalid pull/push data during importing the block");
            autoPopOff(currentBlock.getHeight());
            break;
          }
        } catch (Exception exception) {
          logger.error("Uncaught exception in blockImporterThread", exception);
        }
      }
    };
    threadPool.scheduleThread("ImportBlocks", blockImporterThread, Constants.BLOCK_PROCESS_THREAD_DELAY, TimeUnit.MILLISECONDS);
    //Is there anything to verify
    //should we use Ocl?
    //is Ocl ready ?
    //verify using java
    Runnable pocVerificationThread = () -> {
      boolean verifyWithOcl;
      int queueThreshold = oclVerify ? oclUnverifiedQueue : 0;

      while (!Thread.interrupted() && ThreadPool.running.get()) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
        int unVerified = downloadCache.getUnverifiedSize();
        if (unVerified > queueThreshold) { //Is there anything to verify
          if (unVerified >= oclUnverifiedQueue && oclVerify) { //should we use Ocl?
            verifyWithOcl = true;
            if (!gpuUsage.tryAcquire()) { //is Ocl ready ?
              logger.debug("already max locked");
              verifyWithOcl = false;
            }
          } else {
            verifyWithOcl = false;
          }
          if (verifyWithOcl) {
            int poCVersion;
            int pos = 0;
            List<Block> blocks = new LinkedList<>();
            poCVersion = downloadCache.getPoCVersion(downloadCache.getUnverifiedBlockIdFromPos(0));
            while (!Thread.interrupted() && ThreadPool.running.get()
                    && (downloadCache.getUnverifiedSize() - 1) > pos
                    && blocks.size() < OCLPoC.getMaxItems()) {
              long blockId = downloadCache.getUnverifiedBlockIdFromPos(pos);
              if (downloadCache.getPoCVersion(blockId) != poCVersion) {
                break;
              }
              blocks.add(downloadCache.getBlock(blockId));
              pos += 1;
            }
            try {
              OCLPoC.validatePoC(blocks, poCVersion, blockService);
              downloadCache.removeUnverifiedBatch(blocks);
            } catch (OCLPoC.PreValidateFailException e) {
              logger.info(e.toString(), e);
              blacklistClean(e.getBlock(), e, "found invalid pull/push data during processing the pocVerification");
            } catch (OCLPoC.OCLCheckerException e) {
              logger.info("Open CL error. slow verify will occur for the next " + oclUnverifiedQueue + " Blocks", e);
            } catch (Exception e) {
              logger.info("Unspecified Open CL error: ", e);
            } finally {
              gpuUsage.release();
            }
          } else { //verify using java
            try {
              blockService.preVerify(downloadCache.getFirstUnverifiedBlock());
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (BlockNotAcceptedException e) {
              logger.error("Block failed to preverify: ", e);
            }
          }
        }
      }
    };
    if (propertyService.getBoolean(Props.GPU_ACCELERATION)) {
      logger.debug("Starting preverifier thread in Open CL mode.");
      threadPool.scheduleThread("VerifyPoc", pocVerificationThread, Constants.BLOCK_PROCESS_THREAD_DELAY, TimeUnit.MILLISECONDS);
    } else {
      logger.debug("Starting preverifier thread in CPU mode.");
      threadPool.scheduleThreadCores(pocVerificationThread, Constants.BLOCK_PROCESS_THREAD_DELAY, TimeUnit.MILLISECONDS);
    }
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
    logger.debug("Blacklisted peer and cleaned queue");
  }

  private void autoPopOff(int height) {
    if (!autoPopOffEnabled) {
      logger.warn("Not automatically popping off as it is disabled via properties. If your node becomes stuck you will need to manually pop off.");
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
        logger.warn("Popping off {} blocks due to previous failures to push this block", autoPopOffNumberOfBlocks);
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
  public void processPeerBlock(JsonObject request, Peer peer) throws BurstException {
    Block newBlock = Block.parseBlock(request, blockchain.getHeight());
     //* This process takes care of the blocks that is announced by peers We do not want to be fed forks.
    Block chainblock = downloadCache.getLastBlock();
    if (chainblock.getId() == newBlock.getPreviousBlockId()) {
      newBlock.setHeight(chainblock.getHeight() + 1);
      newBlock.setByteLength(newBlock.toString().length());
      blockService.calculateBaseTarget(newBlock, chainblock);
      downloadCache.addBlock(newBlock);
      logger.debug("Peer {} added block from Announce: Id: {} Height: {}", peer.getPeerAddress(), newBlock.getId(), newBlock.getHeight());
    } else {
      logger.debug("Peer {} sent us block: {} which is not the follow-up block for {}", peer.getPeerAddress(), newBlock.getPreviousBlockId(), chainblock.getId());
    }
  }

  @Override
  public List<Block> popOffTo(int height) {
    return popOffTo(blockchain.getBlockAtHeight(height));
  }

  @Override
  public void fullReset() {
    blockDb.deleteAll(false);
    dbCacheManager.flushCache();
    downloadCache.resetCache();
    addGenesisBlock();
  }

  void setGetMoreBlocks(boolean getMoreBlocks) {
    this.getMoreBlocks.set(getMoreBlocks);
  }

  private void addBlock(Block block) {
    blockchainStore.addBlock(block);
    blockchain.setLastBlock(block);
  }

  private void addGenesisBlock() {
    if (blockDb.hasBlock(Genesis.GENESIS_BLOCK_ID)) {
      logger.info("Genesis block already in database");
      Block lastBlock = blockDb.findLastBlock();
      blockchain.setLastBlock(lastBlock);
      logger.info("Last block height: {}", lastBlock.getHeight());
      return;
    }
    logger.info("Genesis block not in database, starting from scratch");
    try {
      List<Transaction> transactions = new ArrayList<>();
      MessageDigest digest = Crypto.sha256();
      transactions.forEach(transaction -> digest.update(transaction.getBytes()));
      ByteBuffer bf = ByteBuffer.allocate(0);
      bf.order(ByteOrder.LITTLE_ENDIAN);
      byte[] byteATs = bf.array();
      Block genesisBlock = new Block(-1, 0, 0, 0, 0, transactions.size() * 128,
          digest.digest(), Genesis.getCreatorPublicKey(), new byte[32],
          Genesis.getGenesisBlockSignature(), null, transactions, 0, byteATs, -1);
      blockService.setPrevious(genesisBlock, null);
      addBlock(genesisBlock);
    } catch (BurstException.ValidationException e) {
      logger.info(e.getMessage());
      throw new RuntimeException(e.toString(), e);
    }
  }

  private void pushBlock(final Block block) throws BlockNotAcceptedException {
    synchronized (transactionProcessor.getUnconfirmedTransactionsSyncObj()) {
      stores.beginTransaction();
      int curTime = timeService.getEpochTime();

      Block previousLastBlock = null;
      try {

        previousLastBlock = blockchain.getLastBlock();

        if (previousLastBlock.getId() != block.getPreviousBlockId()) {
          throw new BlockOutOfOrderException(
              "Previous block id doesn't match for block " + block.getHeight()
                  + ((previousLastBlock.getHeight() + 1) == block.getHeight() ? "" : " invalid previous height " + previousLastBlock.getHeight())
          );
        }

        if (block.getVersion() != getBlockVersion()) {
          throw new BlockNotAcceptedException("Invalid version " + block.getVersion() + " for block " + block.getHeight());
        }

        if (block.getVersion() != 1
            && !Arrays.equals(Crypto.sha256().digest(previousLastBlock.getBytes()),
            block.getPreviousBlockHash())) {
          throw new BlockNotAcceptedException("Previous block hash doesn't match for block " + block.getHeight());
        }
        if (block.getTimestamp() > curTime + MAX_TIMESTAMP_DIFFERENCE
            || block.getTimestamp() <= previousLastBlock.getTimestamp()) {
          throw new BlockOutOfOrderException("Invalid timestamp: " + block.getTimestamp()
              + " current time is " + curTime
              + ", previous block timestamp is " + previousLastBlock.getTimestamp());
        }
        if (block.getId() == 0L || blockDb.hasBlock(block.getId())) {
          throw new BlockNotAcceptedException("Duplicate block or invalid id for block " + block.getHeight());
        }
        if (!blockService.verifyGenerationSignature(block)) {
          throw new BlockNotAcceptedException("Generation signature verification failed for block " + block.getHeight());
        }
        if (!blockService.verifyBlockSignature(block)) {
          throw new BlockNotAcceptedException("Block signature verification failed for block " + block.getHeight());
        }

        final TransactionDuplicatesCheckerImpl transactionDuplicatesChecker = new TransactionDuplicatesCheckerImpl();
        long calculatedTotalAmount = 0;
        long calculatedTotalFee = 0;
        MessageDigest digest = Crypto.sha256();

        long[] feeArray = new long[block.getTransactions().size()];
        int slotIdx = 0;

        for (Transaction transaction : block.getTransactions()) {
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
            if (transaction.getReferencedTransactionFullHash() != null && ((previousLastBlock.getHeight() < Constants.REFERENCED_TRANSACTION_FULL_HASH_BLOCK
                    && !transactionDb.hasTransaction(
                    Convert.fullHashToId(transaction.getReferencedTransactionFullHash())))
                    || (previousLastBlock
                    .getHeight() >= Constants.REFERENCED_TRANSACTION_FULL_HASH_BLOCK
                    && !hasAllReferencedTransactions(transaction, transaction.getTimestamp(), 0)))) {
                throw new TransactionNotAcceptedException("Missing or invalid referenced transaction "
                        + transaction.getReferencedTransactionFullHash() + " for transaction "
                        + transaction.getStringId(), transaction);
            }
          if (transaction.getVersion() != transactionProcessor.getTransactionVersion(previousLastBlock.getHeight())) {
            throw new TransactionNotAcceptedException("Invalid transaction version "
                + transaction.getVersion() + " at height " + previousLastBlock.getHeight(),
                transaction);
          }

          if (!transactionService.verifyPublicKey(transaction)) {
            throw new TransactionNotAcceptedException("Wrong public key in transaction "
                + transaction.getStringId() + " at height " + previousLastBlock.getHeight(),
                transaction);
          }
            if (Burst.getFluxCapacitor().getValue(FluxValues.AUTOMATED_TRANSACTION_BLOCK) && !economicClustering.verifyFork(transaction)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Block {} height {} contains transaction that was generated on a fork: {} ecBlockId {} ecBlockHeight {}", block.getStringId(), previousLastBlock.getHeight() + 1, transaction.getStringId(), transaction.getECBlockHeight(), Convert.toUnsignedLong(transaction.getECBlockId()));
                }
                throw new TransactionNotAcceptedException("Transaction belongs to a different fork",
                        transaction);
            }
          if (transaction.getId() == 0L) {
            throw new TransactionNotAcceptedException("Invalid transaction id", transaction);
          }

          if (transactionDuplicatesChecker.hasAnyDuplicate(transaction)) {
            throw new TransactionNotAcceptedException("Transaction is a duplicate: " + transaction.getStringId(), transaction);
          }

          try {
            transactionService.validate(transaction);
          } catch (BurstException.ValidationException e) {
            throw new TransactionNotAcceptedException(e.getMessage(), transaction);
          }

          calculatedTotalAmount += transaction.getAmountNQT();
          calculatedTotalFee += transaction.getFeeNQT();
          digest.update(transaction.getBytes());
          indirectIncomingService.processTransaction(transaction);
          feeArray[slotIdx] = transaction.getFeeNQT();
          slotIdx += 1;
        }

        if (calculatedTotalAmount > block.getTotalAmountNQT()
            || calculatedTotalFee > block.getTotalFeeNQT()) {
          throw new BlockNotAcceptedException("Total amount or fee don't match transaction totals for block " + block.getHeight());
        }

        if (Burst.getFluxCapacitor().getValue(FluxValues.NEXT_FORK)) {
          Arrays.sort(feeArray);
          for (int i = 0; i < feeArray.length; i++) {
            if (feeArray[i] >= Constants.FEE_QUANT * (i + 1)) {
              throw new BlockNotAcceptedException("Transaction fee is not enough to be included in this block " + block.getHeight());
            }
          }
        }

        if (!Arrays.equals(digest.digest(), block.getPayloadHash())) {
          throw new BlockNotAcceptedException("Payload hash doesn't match for block " + block.getHeight());
        }

        long remainingAmount = Convert.safeSubtract(block.getTotalAmountNQT(), calculatedTotalAmount);
        long remainingFee = Convert.safeSubtract(block.getTotalFeeNQT(), calculatedTotalFee);

        blockService.setPrevious(block, previousLastBlock);
        blockListeners.notify(block, Event.BEFORE_BLOCK_ACCEPT);
        transactionProcessor.removeForgedTransactions(block.getTransactions());
        transactionProcessor.requeueAllUnconfirmedTransactions();
        accountService.flushAccountTable();
        addBlock(block);
        downloadCache.removeBlock(block); // We make sure downloadCache do not have this block anymore.
        accept(block, remainingAmount, remainingFee);
        derivedTableManager.getDerivedTables().forEach(DerivedTable::finish);
        stores.commitTransaction();
      } catch (BlockNotAcceptedException | ArithmeticException e) {
        stores.rollbackTransaction();
        blockchain.setLastBlock(previousLastBlock);
        downloadCache.resetCache();
        throw e;
      } finally {
        stores.endTransaction();
      }
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
    for (Transaction transaction : block.getTransactions()) {
      if (!transactionService.applyUnconfirmed(transaction)) {
        throw new TransactionNotAcceptedException(
            "Double spending transaction: " + transaction.getStringId(), transaction);
      }
    }
    
    long calculatedRemainingAmount = 0;
    long calculatedRemainingFee = 0;
    // ATs
    AtBlock atBlock;
    AT.clearPendingFees();
    AT.clearPendingTransactions();
    try {
      atBlock = AtController.validateATs(block.getBlockATs(), blockchain.getHeight());
    } catch (AtException e) {
      throw new BlockNotAcceptedException("ats are not matching at block height " + blockchain.getHeight() + " (" + e + ")");
    }
    calculatedRemainingAmount += atBlock.getTotalAmount();
    calculatedRemainingFee += atBlock.getTotalFees();
    // ATs
    if (subscriptionService.isEnabled()) {
      calculatedRemainingFee += subscriptionService.applyUnconfirmed(block.getTimestamp());
    }
    if (remainingAmount != null && remainingAmount != calculatedRemainingAmount) {
      throw new BlockNotAcceptedException("Calculated remaining amount doesn't add up for block " + block.getHeight());
    }
    if (remainingFee != null && remainingFee != calculatedRemainingFee) {
      throw new BlockNotAcceptedException("Calculated remaining fee doesn't add up for block " + block.getHeight());
    }
    blockListeners.notify(block, Event.BEFORE_BLOCK_APPLY);
    blockService.apply(block);
    subscriptionService.applyConfirmed(block, blockchain.getHeight());
    if (escrowService.isEnabled()) {
      escrowService.updateOnBlock(block, blockchain.getHeight());
    }
    blockListeners.notify(block, Event.AFTER_BLOCK_APPLY);
    if (! block.getTransactions().isEmpty()) {
      transactionProcessor.notifyListeners(block.getTransactions(),
          TransactionProcessor.Event.ADDED_CONFIRMED_TRANSACTIONS);
    }
  }

  private List<Block> popOffTo(Block commonBlock) {
  
    if (commonBlock.getHeight() < getMinRollbackHeight()) {
        throw new IllegalArgumentException("Rollback to height " + commonBlock.getHeight() + " not suppported, " + "current height " + blockchain.getHeight());
    }
    if (!blockchain.hasBlock(commonBlock.getId())) {
      logger.debug("Block {} not found in blockchain, nothing to pop off", commonBlock.getStringId());
      return Collections.emptyList();
    }
    List<Block> poppedOffBlocks = new ArrayList<>();
    synchronized (downloadCache) {
      synchronized (transactionProcessor.getUnconfirmedTransactionsSyncObj()) {
        try {
          stores.beginTransaction();
          Block block = blockchain.getLastBlock();
          logger.info("Rollback from {} to {}", block.getHeight(), commonBlock.getHeight());
          while (block.getId() != commonBlock.getId() && block.getId() != Genesis.GENESIS_BLOCK_ID) {
            poppedOffBlocks.add(block);
            block = popLastBlock();
          }
          derivedTableManager.getDerivedTables().forEach(table -> table.rollback(commonBlock.getHeight()));
          dbCacheManager.flushCache();
          stores.commitTransaction();
          downloadCache.resetCache();
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
    if (block.getId() == Genesis.GENESIS_BLOCK_ID) {
      throw new RuntimeException("Cannot pop off genesis block");
    }
    Block previousBlock = blockDb.findBlock(block.getPreviousBlockId());
    blockchain.setLastBlock(block, previousBlock);
    block.getTransactions().forEach(Transaction::unsetBlock);
    blockDb.deleteBlocksFrom(block.getId());
    blockListeners.notify(block, Event.BLOCK_POPPED);
    return previousBlock;
  }

  private int getBlockVersion() {
    return 3;
  }

  private boolean preCheckUnconfirmedTransaction(TransactionDuplicatesCheckerImpl transactionDuplicatesChecker, UnconfirmedTransactionStore unconfirmedTransactionStore, Transaction transaction) {
    boolean ok = hasAllReferencedTransactions(transaction, transaction.getTimestamp(), 0)
            && !transactionDuplicatesChecker.hasAnyDuplicate(transaction)
            && !transactionDb.hasTransaction(transaction.getId());
    if (!ok) unconfirmedTransactionStore.remove(transaction);
    return ok;
  }

  @Override
  public void generateBlock(String secretPhrase, byte[] publicKey, Long nonce) throws BlockNotAcceptedException {
    synchronized (downloadCache) {
      downloadCache.lockCache(); //stop all incoming blocks.
      UnconfirmedTransactionStore unconfirmedTransactionStore = stores.getUnconfirmedTransactionStore();
      SortedSet<Transaction> orderedBlockTransactions = new TreeSet<>();

      int blockSize   = Burst.getFluxCapacitor().getValue(FluxValues.MAX_NUMBER_TRANSACTIONS);
      int payloadSize = Burst.getFluxCapacitor().getValue(FluxValues.MAX_PAYLOAD_LENGTH);

      long totalAmountNQT = 0;
      long totalFeeNQT = 0;

      final Block previousBlock = blockchain.getLastBlock();
      final int blockTimestamp = timeService.getEpochTime();

      // this is just an validation. which collects all valid transactions, which fit into the block
      // finally all stuff is reverted so nothing is written to the db
      // the block itself with all transactions we found is pushed using pushBlock which calls
      // accept (so it's going the same way like a received/synced block)
      try {
        stores.beginTransaction();

        final TransactionDuplicatesCheckerImpl transactionDuplicatesChecker = new TransactionDuplicatesCheckerImpl();

        ToLongFunction<Transaction> priorityCalculator = transaction -> {
          int age = blockTimestamp + 1 - transaction.getTimestamp();
          if (age < 0) age = 1;
          return ((long) age) * transaction.getFeeNQT();
        };

        // Map of slot number -> transaction
        Map<Long, Transaction> transactionsToBeIncluded;
        Stream<Transaction> inclusionCandidates = unconfirmedTransactionStore.getAll().stream()
                .filter(transaction -> // Normal filtering
                        transaction.getVersion() == transactionProcessor.getTransactionVersion(previousBlock.getHeight())
                                && transaction.getExpiration() >= blockTimestamp
                                && transaction.getTimestamp() <= blockTimestamp + MAX_TIMESTAMP_DIFFERENCE
                                && (
                                !Burst.getFluxCapacitor().getValue(FluxValues.AUTOMATED_TRANSACTION_BLOCK)
                                        || economicClustering.verifyFork(transaction)
                        ))
                .filter(transaction -> preCheckUnconfirmedTransaction(transactionDuplicatesChecker, unconfirmedTransactionStore, transaction)); // Extra check for transactions that are to be considered

        if (Burst.getFluxCapacitor().getValue(FluxValues.PRE_DYMAXION)) {
          // In this step we get all unconfirmed transactions and then sort them by slot, followed by priority
          Map<Long, Map<Long, Transaction>> unconfirmedTransactionsOrderedBySlotThenPriority = new HashMap<>();
            inclusionCandidates.collect(Collectors.toMap(Function.identity(), priorityCalculator::applyAsLong)).forEach((transaction, priority) -> {
            long slot = (transaction.getFeeNQT() - (transaction.getFeeNQT() % FEE_QUANT)) / FEE_QUANT;
            unconfirmedTransactionsOrderedBySlotThenPriority.computeIfAbsent(slot, k -> new HashMap<>());
            unconfirmedTransactionsOrderedBySlotThenPriority.get(slot).put(priority, transaction);
          });

          // In this step we sort through each slot and find the highest priority transaction in each.
          AtomicLong highestSlot = new AtomicLong();
          unconfirmedTransactionsOrderedBySlotThenPriority.keySet()
                  .forEach(slot -> {
                    if (highestSlot.get() < slot) {
                      highestSlot.set(slot);
                    }
                  });
          List<Long> slotsWithNoTransactions = new ArrayList<>();
          for (long slot = 1; slot <= highestSlot.get(); slot++) {
            Map<Long, Transaction> transactions = unconfirmedTransactionsOrderedBySlotThenPriority.get(slot);
            if (transactions == null || transactions.size() == 0) {
              slotsWithNoTransactions.add(slot);
            }
          }
          Map<Long, Transaction> unconfirmedTransactionsOrderedBySlot = new HashMap<>();
          unconfirmedTransactionsOrderedBySlotThenPriority.forEach((slot, transactions) -> {
            AtomicLong highestPriority = new AtomicLong();
            transactions.keySet().forEach(priority -> {
              if (highestPriority.get() < priority) {
                highestPriority.set(priority);
              }
            });
            unconfirmedTransactionsOrderedBySlot.put(slot, transactions.get(highestPriority.get()));
            transactions.remove(highestPriority.get()); // This is to help with filling slots with no transactions
          });

          // If a slot does not have any transactions in it, the next highest priority transaction from the slot above should be used.
          slotsWithNoTransactions.sort(Comparator.reverseOrder());
          slotsWithNoTransactions.forEach(emptySlot -> {
            long slotNumberToTakeFrom = emptySlot;
            Map<Long, Transaction> slotToTakeFrom = null;
            while (slotToTakeFrom == null || slotToTakeFrom.size() == 0) {
              slotNumberToTakeFrom++;
              if (slotNumberToTakeFrom > highestSlot.get()) return;
              slotToTakeFrom = unconfirmedTransactionsOrderedBySlotThenPriority.get(slotNumberToTakeFrom);
            }
            AtomicLong highestPriority = new AtomicLong();
            slotToTakeFrom.keySet().forEach(priority -> {
              if (highestPriority.get() < priority) {
                highestPriority.set(priority);
              }
            });
            unconfirmedTransactionsOrderedBySlot.put(emptySlot, slotToTakeFrom.get(highestPriority.get()));
            slotToTakeFrom.remove(highestPriority.get());
          });
          transactionsToBeIncluded = unconfirmedTransactionsOrderedBySlot;
        } else { // Before Pre-Dymaxion HF, just choose highest priority
          Map<Long, Transaction> transactionsOrderedByPriority = inclusionCandidates.collect(Collectors.toMap(priorityCalculator::applyAsLong, Function.identity()));
          Map<Long, Transaction> transactionsOrderedBySlot = new HashMap<>();
          AtomicLong currentSlot = new AtomicLong(1);
          transactionsOrderedByPriority.keySet()
                  .stream()
                  .sorted(Comparator.reverseOrder())
                  .forEach(priority -> { // This should do highest priority to lowest priority
                    transactionsOrderedBySlot.put(currentSlot.get(), transactionsOrderedByPriority.get(priority));
                    currentSlot.incrementAndGet();
                  });
          transactionsToBeIncluded = transactionsOrderedBySlot;
        }

        for (Map.Entry<Long, Transaction> entry : transactionsToBeIncluded.entrySet()) {
          long slot = entry.getKey();
          Transaction transaction = entry.getValue();

          if (blockSize <= 0 || payloadSize <= 0) {
            break;
          } else if (transaction.getSize() > payloadSize) {
            continue;
          }

          long slotFee = Burst.getFluxCapacitor().getValue(FluxValues.PRE_DYMAXION) ? slot * FEE_QUANT : ONE_BURST;
          if (transaction.getFeeNQT() >= slotFee) {
            if (transactionService.applyUnconfirmed(transaction)) {
              try {
                transactionService.validate(transaction);
                payloadSize -= transaction.getSize();
                totalAmountNQT += transaction.getAmountNQT();
                totalFeeNQT += transaction.getFeeNQT();
                orderedBlockTransactions.add(transaction);
                blockSize--;
              } catch (BurstException.NotCurrentlyValidException e) {
                transactionService.undoUnconfirmed(transaction);
              } catch (BurstException.ValidationException e) {
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
          totalFeeNQT += subscriptionService.calculateFees(blockTimestamp);
        }
      }
      catch (Exception e) {
        stores.rollbackTransaction();
        throw e;
      }
      finally {
        stores.rollbackTransaction();
        stores.endTransaction();
      }

      // ATs for block
      AT.clearPendingFees();
      AT.clearPendingTransactions();
      AtBlock atBlock = AtController.getCurrentBlockATs(payloadSize, previousBlock.getHeight() + 1);
      byte[] byteATs = atBlock.getBytesForBlock();

      // digesting AT Bytes
      if (byteATs != null) {
        payloadSize    -= byteATs.length;
        totalFeeNQT    += atBlock.getTotalFees();
        totalAmountNQT += atBlock.getTotalAmount();
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
            previousBlock.getId(), totalAmountNQT, totalFeeNQT, Burst.getFluxCapacitor().getValue(FluxValues.MAX_PAYLOAD_LENGTH) - payloadSize, payloadHash, publicKey,
            generationSignature, null, previousBlockHash, new ArrayList<>(orderedBlockTransactions), nonce,
            byteATs, previousBlock.getHeight());
      } catch (BurstException.ValidationException e) {
        // shouldn't happen because all transactions are already validated
        logger.info("Error generating block", e);
        return;
      }
      block.sign(secretPhrase);
      blockService.setPrevious(block, previousBlock);
      try {
        blockService.preVerify(block);
        pushBlock(block);
        blockListeners.notify(block, Event.BLOCK_GENERATED);
        if (logger.isDebugEnabled()) {
            logger.debug("Account {} generated block {} at height {}", Convert.toUnsignedLong(block.getGeneratorId()), block.getStringId(), block.getHeight());
        }
        downloadCache.resetCache();
      } catch ( InterruptedException e ) {
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
    } //end synchronized cache
  }

  private boolean hasAllReferencedTransactions(Transaction transaction, int timestamp, int count) {
    if (transaction.getReferencedTransactionFullHash() == null) {
      return timestamp - transaction.getTimestamp() < 60 * 1440 * 60 && count < 10;
    }
    transaction = transactionDb.findTransactionByFullHash(transaction.getReferencedTransactionFullHash());
    if (!subscriptionService.isEnabled() && transaction != null && transaction.getSignature() == null) {
      transaction = null;
    }
    return transaction != null && hasAllReferencedTransactions(transaction, timestamp, count + 1);
  }
}
