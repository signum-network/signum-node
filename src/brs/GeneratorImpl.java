package brs;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brs.Attachment.CommitmentAdd;
import brs.Attachment.CommitmentRemove;
import brs.crypto.Crypto;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.AccountService;
import brs.services.TimeService;
import brs.util.Convert;
import brs.util.DownloadCacheImpl;
import brs.util.Listener;
import brs.util.Listeners;
import brs.util.MiningPlot;
import brs.util.ThreadPool;
import signumj.crypto.SignumCrypto;
import signumj.entity.SignumID;

public class GeneratorImpl implements Generator {
  private static final Logger logger = LoggerFactory.getLogger(GeneratorImpl.class);

  private final Listeners<GeneratorState, Event> listeners = new Listeners<>();
  private final ConcurrentMap<Long, GeneratorStateImpl> generators = new ConcurrentHashMap<>();
  private final SignumCrypto signumCrypto = SignumCrypto.getInstance();
  private final Blockchain blockchain;
  private final DownloadCacheImpl downloadCache;
  private final TimeService timeService;
  private final FluxCapacitor fluxCapacitor;

  public GeneratorImpl(Blockchain blockchain, DownloadCacheImpl downloadCache, AccountService accountService, TimeService timeService, FluxCapacitor fluxCapacitor) {
    this.blockchain = blockchain;
    this.downloadCache = downloadCache;
    this.timeService = timeService;
    this.fluxCapacitor = fluxCapacitor;
  }

  private Runnable generateBlockThread(BlockchainProcessor blockchainProcessor) {
    return () -> {
      if (blockchainProcessor.isScanning()) {
        return;
      }
      try {
        long currentBlock = blockchain.getLastBlock().getHeight();
        Iterator<Entry<Long, GeneratorStateImpl>> it = generators.entrySet().iterator();
        while (it.hasNext() && !Thread.currentThread().isInterrupted() && ThreadPool.running.get()) {
          Entry<Long, GeneratorStateImpl> generator = it.next();
          if (currentBlock < generator.getValue().getBlock()) {
            generator.getValue().forge(blockchainProcessor);
          } else {
            it.remove();
          }
        }
      } catch (BlockchainProcessor.BlockNotAcceptedException e) {
        logger.debug("Error in block generation thread", e);
        generators.clear();
      }
    };
  }

  @Override
  public void generateForBlockchainProcessor(ThreadPool threadPool, BlockchainProcessor blockchainProcessor) {
    threadPool.scheduleThread("GenerateBlocks", generateBlockThread(blockchainProcessor), 500, TimeUnit.MILLISECONDS);
  }

  @Override
  public boolean addListener(Listener<GeneratorState> listener, Event eventType) {
    return listeners.addListener(listener, eventType);
  }

  @Override
  public boolean removeListener(Listener<GeneratorState> listener, Event eventType) {
    return listeners.removeListener(listener, eventType);
  }

  @Override
  public GeneratorState addNonce(String secretPhrase, Long nonce) {
    byte[] publicKey = Crypto.getPublicKey(secretPhrase);
    return addNonce(secretPhrase, nonce, publicKey);
  }

  @Override
  public GeneratorState addNonce(String secretPhrase, Long nonce, byte[] publicKey) {
    byte[] publicKeyHash = Crypto.sha256().digest(publicKey);
    long id = Convert.fullHashToId(publicKeyHash);

    GeneratorStateImpl generator = new GeneratorStateImpl(secretPhrase, nonce, publicKey, id);
    GeneratorStateImpl curGen = generators.get(id);
    if (curGen == null || generator.getBlock() > curGen.getBlock() || generator.getDeadline().compareTo(curGen.getDeadline()) < 0) {
      generators.put(id, generator);
      listeners.notify(generator, Event.NONCE_SUBMITTED);
      if (logger.isDebugEnabled()) {
        logger.debug("Account {} started mining, deadline {} seconds", Convert.toUnsignedLong(id), generator.getDeadline());
      }
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("Account {} already has a better nonce", Convert.toUnsignedLong(id));
      }
    }

    return generator;
  }

  @Override
  public Collection<GeneratorState> getAllGenerators() {
    return Collections.unmodifiableCollection(generators.values());
  }

  @Override
  public byte[] calculateGenerationSignature(byte[] lastGenSig, long lastGenId) {
    return signumCrypto.calculateGenerationSignature(lastGenSig, lastGenId);
  }

  @Override
  public int calculateScoop(byte[] genSig, long height) {
    return signumCrypto.calculateScoop(genSig, height);
  }

  private int getPocVersion(int blockHeight) {
    return fluxCapacitor.getValue(FluxValues.POC2, blockHeight) ? 2 : 1;
  }

  @Override
  public BigInteger calculateHit(long accountId, long nonce, byte[] genSig, int scoop, int blockHeight) {
    return signumCrypto.calculateHit(accountId, nonce, genSig, scoop, getPocVersion(blockHeight));
  }

  @Override
  public BigInteger calculateHit(byte[] genSig, byte[] scoopData) {
    return signumCrypto.calculateHit(genSig, scoopData);
  }

  @Override
  public byte[] calculateScoops(long[] accountIds, long[] nonces, int[] scoops, int[] blockHeights) {
    int count = accountIds.length;
    if (nonces.length != count || scoops.length != count || blockHeights.length != count) {
      throw new IllegalArgumentException("accountIds, nonces, scoops and blockHeights must have the same length");
    }
    byte[] result = new byte[count * MiningPlot.SCOOP_SIZE];
    // usually all blocks of a batch have the same PoC version, otherwise one call per version
    for (int pocVersion = 1; pocVersion <= 2; pocVersion++) {
      final int version = pocVersion;
      int[] indexes = IntStream.range(0, count).filter(i -> getPocVersion(blockHeights[i]) == version).toArray();
      if (indexes.length == 0) {
        continue;
      }
      byte[] group = signumCrypto.calculateScoops(
          Arrays.stream(indexes).mapToLong(i -> accountIds[i]).toArray(),
          Arrays.stream(indexes).mapToLong(i -> nonces[i]).toArray(),
          Arrays.stream(indexes).map(i -> scoops[i]).toArray(),
          pocVersion);
      for (int j = 0; j < indexes.length; j++) {
        System.arraycopy(group, j * MiningPlot.SCOOP_SIZE, result, indexes[j] * MiningPlot.SCOOP_SIZE, MiningPlot.SCOOP_SIZE);
      }
    }
    return result;
  }

  @Override
  public double getCommitmentFactor(long commitment, long averageCommitment, int blockHeight) {
    if(fluxCapacitor.getValue(FluxValues.POC_PLUS, blockHeight)) {
      double commitmentFactor = ((double)commitment)/averageCommitment;
      commitmentFactor = Math.pow(commitmentFactor, 0.4515449935);
      commitmentFactor = Math.min(8.0, commitmentFactor);
      commitmentFactor = Math.max(0.125, commitmentFactor);

      return commitmentFactor;
    }
    return 1.0;
  }

  @Override
  public BigInteger calculateDeadline(BigInteger hit, long capacityBaseTarget, long commitment, long averageCommitment, int blockHeight) {
    BigInteger deadline = hit.divide(BigInteger.valueOf(capacityBaseTarget));

    double blockTime = fluxCapacitor.getValue(FluxValues.BLOCK_TIME);
    double lnScale = (blockTime) / Math.log(blockTime);

    if(fluxCapacitor.getValue(FluxValues.POC_PLUS, blockHeight)) {
      // private static final double lnScale = 49d; // value that would keep the legacy network size estimation close to real capacity

      double commitmentFactor = getCommitmentFactor(commitment, averageCommitment, blockHeight);

      double nextDeadline = deadline.doubleValue()/commitmentFactor;
      if(nextDeadline > 0) {
        // Avoid zero logarithm
        nextDeadline = Math.log(nextDeadline) * lnScale;
      }
      deadline = BigInteger.valueOf((long)(nextDeadline));
    }
    else if(fluxCapacitor.getValue(FluxValues.SODIUM, blockHeight)) {
      if(deadline.bitLength() < 100 && deadline.longValue() > 0L) {
    	  // Avoid the double precision limit for extremely large numbers (of no value) and zero logarithm
    	  double sodiumDeadline = Math.log(deadline.doubleValue()) * lnScale;
    	  deadline = BigInteger.valueOf((long)sodiumDeadline);
      }
    }
    return deadline;
  }

  public class GeneratorStateImpl implements GeneratorState {
    private final Long accountId;
    private final String secretPhrase;
    private final byte[] publicKey;
    private final BigInteger deadline;
    private final BigInteger hit;
    private final long baseTarget;
    private final long nonce;
    private final long block;

    private GeneratorStateImpl(String secretPhrase, Long nonce, byte[] publicKey, Long account) {
      this.secretPhrase = secretPhrase;
      this.publicKey = publicKey;
      // need to store publicKey in addition to accountId, because the account may not have had its publicKey set yet
      this.accountId = account;
      this.nonce = nonce;

      Block lastBlock = blockchain.getLastBlock();

      this.block = (long) lastBlock.getHeight() + 1;

      byte[] lastGenSig = lastBlock.getGenerationSignature();
      Long lastGenerator = lastBlock.getGeneratorId();

      byte[] newGenSig = calculateGenerationSignature(lastGenSig, lastGenerator);

      int scoopNum = calculateScoop(newGenSig, lastBlock.getHeight() + 1L);

      baseTarget = lastBlock.getCapacityBaseTarget();
      hit = calculateHit(accountId, nonce, newGenSig, scoopNum, lastBlock.getHeight() + 1);
      long commitmment = 0L;
      if(fluxCapacitor.getValue(FluxValues.POC_PLUS, lastBlock.getHeight() + 1)) {
        commitmment = estimateCommitment(accountId, lastBlock);
      }

      deadline = calculateDeadline(hit, baseTarget, commitmment, lastBlock.getAverageCommitment(), lastBlock.getHeight() + 1);
    }

    @Override
    public byte[] getPublicKey() {
      return publicKey;
    }

    @Override
    public Long getAccountId() {
      return accountId;
    }

    @Override
    public BigInteger getDeadline() {
      return deadline;
    }

    @Override
    public BigInteger getDeadlineLegacy() {
      return hit.divide(BigInteger.valueOf(baseTarget));
    }

    @Override
    public long getBlock() {
      return block;
    }

    private void forge(BlockchainProcessor blockchainProcessor) throws BlockchainProcessor.BlockNotAcceptedException {
      Block lastBlock = blockchain.getLastBlock();

      int elapsedTime = timeService.getEpochTime() - lastBlock.getTimestamp();
      if (BigInteger.valueOf(elapsedTime).compareTo(deadline) > 0) {
        blockchainProcessor.generateBlock(secretPhrase, publicKey, nonce);
      }
    }
  }

  public static class MockGenerator extends GeneratorImpl {
    private final PropertyService propertyService;
    public MockGenerator(PropertyService propertyService, Blockchain blockchain, AccountService accountService, TimeService timeService, FluxCapacitor fluxCapacitor) {
      super(blockchain, null, accountService, timeService, fluxCapacitor);
      this.propertyService = propertyService;
    }

    @Override
    public BigInteger calculateHit(long accountId, long nonce, byte[] genSig, int scoop, int blockHeight) {
      return BigInteger.valueOf(propertyService.getInt(Props.DEV_MOCK_MINING_DEADLINE));
    }

    @Override
    public BigInteger calculateHit(byte[] genSig, byte[] scoopData) {
      return BigInteger.valueOf(propertyService.getInt(Props.DEV_MOCK_MINING_DEADLINE));
    }

    // the hit is mocked, so the scoops do not matter: skip the expensive calculation
    @Override
    public byte[] calculateScoops(long[] accountIds, long[] nonces, int[] scoops, int[] blockHeights) {
      return new byte[accountIds.length * MiningPlot.SCOOP_SIZE];
    }

    @Override
    public BigInteger calculateDeadline(BigInteger hit, long capacityBaseTarget, long commitment, long averageCommitment, int blockHeight) {
      return BigInteger.valueOf(propertyService.getInt(Props.DEV_MOCK_MINING_DEADLINE));
    }
  }

  @Override
  public long estimateCommitment(long generatorId, Block previousBlock) {
    // Check on the number of blocks mined to estimate the capacity and also the committed balance
    int nBlocksMined = 0;
    int nBlocksMinedOnCache = 1; // the present block being mined
    int nBlocksMinedOnCacheMid = 0;
    int nBlocksMinedOnCacheMax = 0;
    long committedAmount = 0;
    long committedAmountOnCache = 0;
    int capacityEstimationBlocks = Constants.CAPACITY_ESTIMATION_BLOCKS;
    long capacityBaseTarget = previousBlock.getCapacityBaseTarget();
    int height = previousBlock.getHeight();
    int endHeight = height;

    int commitmentWait = fluxCapacitor.getValue(FluxValues.COMMITMENT_WAIT, height);

    // Check if there are mined blocks on the download cache or commitment removals
    Block blockIt = null;
    if(downloadCache != null) {
      blockIt = downloadCache.getBlock(previousBlock.getId());
    }
    // Get some pending from cache and later from DB directly when available
    // We also check if the blockchain actually have blockIt, we might be processing a fork
    while(blockIt != null && (endHeight >= blockchain.getHeight() ||
        blockchain.getBlockIdAtHeight(blockIt.getHeight()) != blockIt.getId())) {
      if(blockIt.getGeneratorId() == generatorId) {
        if(height - endHeight <= Constants.CAPACITY_ESTIMATION_BLOCKS)
          nBlocksMinedOnCache++;
        else {
          if(fluxCapacitor.getValue(FluxValues.SPEEDWAY, height) && height - endHeight <= Constants.CAPACITY_ESTIMATION_BLOCKS_MID) {
            nBlocksMinedOnCacheMid++;
          }
          else {
            nBlocksMinedOnCacheMax++;
          }
        }
      }
      for(Transaction tx : blockIt.getTransactions()) {
        if(tx.getSenderId() == generatorId) {
          if(blockIt.getHeight() <= height - commitmentWait && tx.getType() == TransactionType.SignaMining.COMMITMENT_ADD) {
            CommitmentAdd txAttachment = (CommitmentAdd) tx.getAttachment();
            committedAmountOnCache += txAttachment.getAmountNqt();
          }
          if(tx.getType() == TransactionType.SignaMining.COMMITMENT_REMOVE) {
            CommitmentRemove txAttachment = (CommitmentRemove) tx.getAttachment();
            committedAmountOnCache -= txAttachment.getAmountNqt();
          }
        }
      }

      endHeight--;
      blockIt = downloadCache.getBlock(blockIt.getPreviousBlockId());
    }

    committedAmount = committedAmountOnCache;
    committedAmount += blockchain.getCommittedAmount(generatorId, height, endHeight, null);
    if(committedAmount <= 0L) {
      if(logger.isDebugEnabled()) {
        logger.debug("Block {}, generator {}, no commitment", height, Convert.toUnsignedLong(generatorId));
      }
      return 0L;
    }

    // First we try to estimate the capacity using more recent blocks only
    nBlocksMined = blockchain.getBlocksCount(generatorId, height - capacityEstimationBlocks, endHeight);
    if(nBlocksMined + nBlocksMinedOnCache < 3) {

      if(fluxCapacitor.getValue(FluxValues.SPEEDWAY, height)) {
        // Use more blocks in the past to make the estimation if that is necessary
        capacityEstimationBlocks = Constants.CAPACITY_ESTIMATION_BLOCKS_MID;
        nBlocksMined = blockchain.getBlocksCount(generatorId, height - capacityEstimationBlocks,
            endHeight) + nBlocksMinedOnCacheMid;

        if(nBlocksMined + nBlocksMinedOnCache < 2) {
          // Use even more blocks in the past to make the estimation if that is necessary
          capacityEstimationBlocks = Constants.CAPACITY_ESTIMATION_BLOCKS_MAX;
          nBlocksMined = blockchain.getBlocksCount(generatorId, height - capacityEstimationBlocks,
              endHeight) + nBlocksMinedOnCacheMid + nBlocksMinedOnCacheMax;
        }
      }
      else {
        // Use more blocks in the past to make the estimation if that is necessary
        capacityEstimationBlocks = Constants.CAPACITY_ESTIMATION_BLOCKS_MAX;
        nBlocksMined = blockchain.getBlocksCount(generatorId, height - capacityEstimationBlocks,
            endHeight) + nBlocksMinedOnCacheMax;
      }
    }
    nBlocksMined += nBlocksMinedOnCache;

    long genesisTarget = Constants.INITIAL_BASE_TARGET;
    genesisTarget = (long)(genesisTarget / 1.83d); // account for Sodium deadlines
    long estimatedCapacityGb = genesisTarget*nBlocksMined*1000L/(capacityBaseTarget * capacityEstimationBlocks);
    long minCapacity = fluxCapacitor.getValue(FluxValues.MIN_CAPACITY);
    if(estimatedCapacityGb < minCapacity) {
      estimatedCapacityGb = minCapacity;
    }
    // Commitment being the committed balance per TiB
    long commitment = (committedAmount/estimatedCapacityGb) * 1000L;

    if(logger.isDebugEnabled()) {
      logger.debug("Block {}, Network {} TiB, ID {}, forged {}/{} blocks, {} TiB, committedAmountNQT {}, commitmentNQT {}",
          height,
          (double)genesisTarget/capacityBaseTarget,
          SignumID.fromLong(generatorId).getID(),
          nBlocksMined, capacityEstimationBlocks, estimatedCapacityGb/1000D,
          committedAmount,
          commitment);
    }

    return commitment;
  }
}
