package brs.services.impl;

import brs.*;
import brs.BlockchainProcessor.BlockOutOfOrderException;
import brs.crypto.Crypto;
import brs.fluxcapacitor.FluxValues;
import brs.props.Props;
import brs.services.AccountService;
import brs.services.BlockService;
import brs.services.TransactionService;
import brs.util.Convert;
import brs.util.DownloadCacheImpl;
import brs.util.ThreadPool;

import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BlockServiceImpl implements BlockService {

  private final AccountService accountService;
  private final TransactionService transactionService;
  private final Blockchain blockchain;
  private final DownloadCacheImpl downloadCache;
  private final Generator generator;
  
  private final List<Block> watchedBlocks = new ArrayList<>();

  private static final Logger logger = LoggerFactory.getLogger(BlockServiceImpl.class);

  public BlockServiceImpl(AccountService accountService, TransactionService transactionService, Blockchain blockchain, DownloadCacheImpl downloadCache, Generator generator) {
    this.accountService = accountService;
    this.transactionService = transactionService;
    this.blockchain = blockchain;
    this.downloadCache = downloadCache;
    this.generator = generator;
  }

  @Override
  public boolean verifyBlockSignature(Block block) throws BlockchainProcessor.BlockOutOfOrderException {
    try {
      Block previousBlock = blockchain.getBlock(block.getPreviousBlockId());
      if (previousBlock == null) {
        throw new BlockchainProcessor.BlockOutOfOrderException(
            "Can't verify signature because previous block is missing");
      }

      byte[] data = block.getBytes();
      byte[] data2 = new byte[data.length - 64];
      System.arraycopy(data, 0, data2, 0, data2.length);

      byte[] publicKey = block.getGeneratorPublicKey();
      if(accountService.getAccount(publicKey) != null) {
        // only if the account exists
        Account rewardAccount = getRewardAccount(block);
        publicKey = rewardAccount.getPublicKey();
      }

      return Crypto.verify(block.getBlockSignature(), data2, publicKey, block.getVersion() >= 3);

    } catch (RuntimeException e) {

      logger.info("Error verifying block signature", e);
      return false;

    }
  }

  @Override
  public boolean verifyGenerationSignature(final Block block) throws BlockchainProcessor.BlockNotAcceptedException {
    try {
      Block previousBlock = blockchain.getBlock(block.getPreviousBlockId());

      if (previousBlock == null) {
        throw new BlockchainProcessor.BlockOutOfOrderException(
            "Can't verify generation signature because previous block is missing");
      }

      byte[] correctGenerationSignature = generator.calculateGenerationSignature(
          previousBlock.getGenerationSignature(), previousBlock.getGeneratorId());
      if (!Arrays.equals(block.getGenerationSignature(), correctGenerationSignature)) {
        return false;
      }
      int elapsedTime = block.getTimestamp() - previousBlock.getTimestamp();
      BigInteger hit = block.getPocTime();
      BigInteger pTime = generator.calculateDeadline(hit, previousBlock.getCapacityBaseTarget(), block.getCommitment(), previousBlock.getAverageCommitment(), block.getHeight());
      if(BigInteger.valueOf(elapsedTime).compareTo(pTime) <= 0) {
        logger.info("Elapsed time {} should be higher than the deadline {}", elapsedTime, pTime);
      }
      return BigInteger.valueOf(elapsedTime).compareTo(pTime) > 0;
    } catch (RuntimeException e) {
      logger.info("Error verifying block generation signature", e);
      return false;
    }
  }
  
  private Account getRewardAccount(Block block) {
	Account rewardAccount = accountService.getAccount(block.getGeneratorPublicKey());
    Account.RewardRecipientAssignment rewardAssignment = accountService.getRewardRecipientAssignment(rewardAccount);
    if (rewardAssignment != null) {
      if (block.getHeight() >= rewardAssignment.getFromHeight()) {
        rewardAccount = accountService.getAccount(rewardAssignment.getRecipientId());
      } else {
        rewardAccount = accountService.getAccount(rewardAssignment.getPrevRecipientId());
      }
    }
    return rewardAccount;
  }
  
  @Override
  public void watchBlock(Block block) {
	  watchedBlocks.add(block);
  }

  @Override
  public void preVerify(Block block, Block prevBlock) throws BlockchainProcessor.BlockNotAcceptedException, InterruptedException {
    preVerify(block, prevBlock, null);
  }
  
  @Override
  public void preVerify(Block block, Block prevBlock, byte[] scoopData) throws BlockchainProcessor.BlockNotAcceptedException, InterruptedException {
    // Just in case its already verified
    if (block.isVerified()) {
      return;
    }
    
    if(block.getPreviousBlockId() != prevBlock.getId()) {
      logger.info("Error pre-verifying block, invalid previous block");
      return;
    }
    
    int checkPointHeight = Burst.getPropertyService().getInt(
    		Burst.getPropertyService().getBoolean(Props.DEV_TESTNET) ?
    				Props.DEV_CHECKPOINT_HEIGHT : Props.BRS_CHECKPOINT_HEIGHT);
    try {
      if(block.getHeight() < checkPointHeight) {
        // do not verify the nonce up to the checkpoint block
        block.setPocTime(BigInteger.valueOf(0L));
      }
      else {
    	if(block.getHeight() == checkPointHeight) {
       	    String checkPointHash = Burst.getPropertyService().getString(
    	    		Burst.getPropertyService().getBoolean(Props.DEV_TESTNET) ?
    	    				Props.DEV_CHECKPOINT_HASH : Props.BRS_CHECKPOINT_HASH);

       	    String receivedHash = Hex.toHexString(block.getPreviousBlockHash()); 
    		if(!receivedHash.equals(checkPointHash)) {
    			logger.error("Error pre-verifying checkpoint block {}", block.getHeight());
    			return;
    		}
    		logger.info("Checkpoint block {} with previous block hash {} verified", block.getHeight(), Hex.toHexString(block.getPreviousBlockHash()));
    	}
        // Pre-verify poc:
        if (scoopData == null) {
          block.setPocTime(generator.calculateHit(block.getGeneratorId(), block.getNonce(), block.getGenerationSignature(), getScoopNum(block), block.getHeight()));
        } else {
          block.setPocTime(generator.calculateHit(block.getGenerationSignature(), scoopData));
        }
      }
    } catch (RuntimeException e) {
      logger.info("Error pre-verifying block generation signature", e);
      return;
    }

    for (Transaction transaction : block.getTransactions()) {
      if (!transaction.verifySignature()) {
        if (logger.isInfoEnabled()) {
          logger.info("Bad transaction signature during block pre-verification for tx: {} at block height: {}", Convert.toUnsignedLong(transaction.getId()), block.getHeight());
        }
        throw new BlockchainProcessor.TransactionNotAcceptedException("Invalid signature for tx: " + Convert.toUnsignedLong(transaction.getId()) + " at block height: " + block.getHeight(),
            transaction);
      }
      if (Thread.currentThread().isInterrupted() || ! ThreadPool.running.get() )
        throw new InterruptedException();
    }

  }

  @Override
  public void apply(Block block) {
    Account generatorAccount = accountService.getOrAddAccount(block.getGeneratorId());
    generatorAccount.apply(block.getGeneratorPublicKey(), block.getHeight());
    if (!Burst.getFluxCapacitor().getValue(FluxValues.REWARD_RECIPIENT_ENABLE)) {
      accountService.addToBalanceAndUnconfirmedBalanceNQT(generatorAccount, block.getTotalFeeNQT() + getBlockReward(block));
      accountService.addToForgedBalanceNQT(generatorAccount, block.getTotalFeeNQT() + getBlockReward(block));
    } else {
      Account rewardAccount = getRewardAccount(block);
      accountService.addToBalanceAndUnconfirmedBalanceNQT(rewardAccount, block.getTotalFeeNQT() + getBlockReward(block));
      accountService.addToForgedBalanceNQT(rewardAccount, block.getTotalFeeNQT() + getBlockReward(block));
    }

    for(Transaction transaction : block.getTransactions()) {
      transactionService.apply(transaction);
    }
  }

  @Override
  public long getBlockReward(Block block) {
    return getBlockReward(block.getHeight());
  }

  public static long getBlockReward(int height) {
	if (height == 0 || height >= 1944000) {
	  return 0;
	}
	int month = height / 10800;
	return BigInteger.valueOf(10000).multiply(BigInteger.valueOf(95).pow(month))
	  .divide(BigInteger.valueOf(100).pow(month)).longValue() * Constants.ONE_BURST;
  }

  @Override
  public void setPrevious(Block block, Block previousBlock) {
    if (previousBlock != null) {
      if (previousBlock.getId() != block.getPreviousBlockId()) {
        // shouldn't happen as previous id is already verified, but just in case
        throw new IllegalStateException("Previous block id doesn't match");
      }
      block.setHeight(previousBlock.getHeight() + 1);
      if(block.getBaseTarget() == Constants.INITIAL_BASE_TARGET ) {
        try {
          this.calculateBaseTarget(block, previousBlock);
        } catch (BlockOutOfOrderException e) {
          throw new IllegalStateException(e.toString(), e);
        }
      }
    } else {
      block.setHeight(0);
    }
    block.getTransactions().forEach(transaction -> transaction.setBlock(block));
  }

  @Override
  public void calculateBaseTarget(Block block, Block previousBlock) throws BlockOutOfOrderException {
    if (block.getId() == Genesis.GENESIS_BLOCK_ID && block.getPreviousBlockId() == 0) {
      block.setBaseTarget(Constants.INITIAL_BASE_TARGET);
      block.setCumulativeDifficulty(BigInteger.ZERO);
    } else if (block.getHeight() < 4) {
      block.setBaseTarget(Constants.INITIAL_BASE_TARGET);
      block.setCumulativeDifficulty(previousBlock.getCumulativeDifficulty().add(Convert.two64.divide(BigInteger.valueOf(Constants.INITIAL_BASE_TARGET))));
    } else if (block.getHeight() < Constants.BURST_DIFF_ADJUST_CHANGE_BLOCK && !Burst.getFluxCapacitor().getValue(FluxValues.SODIUM)) {
      Block itBlock = previousBlock;
      BigInteger avgBaseTarget = BigInteger.valueOf(itBlock.getBaseTarget());
      do {
        itBlock = downloadCache.getBlock(itBlock.getPreviousBlockId());
        avgBaseTarget = avgBaseTarget.add(BigInteger.valueOf(itBlock.getBaseTarget()));
      } while (itBlock.getHeight() > block.getHeight() - 4);
      avgBaseTarget = avgBaseTarget.divide(BigInteger.valueOf(4));
      long difTime = (long) block.getTimestamp() - itBlock.getTimestamp();

      long curBaseTarget = avgBaseTarget.longValue();
      long newBaseTarget = BigInteger.valueOf(curBaseTarget).multiply(BigInteger.valueOf(difTime))
          .divide(BigInteger.valueOf(Constants.BURST_BLOCK_TIME * 4)).longValue();
      if (newBaseTarget < 0 || newBaseTarget > Constants.MAX_BASE_TARGET) {
        newBaseTarget = Constants.MAX_BASE_TARGET;
      }
      if (newBaseTarget < (curBaseTarget * 9 / 10)) {
        newBaseTarget = curBaseTarget * 9 / 10;
      }
      if (newBaseTarget == 0) {
        newBaseTarget = 1;
      }
      long twofoldCurBaseTarget = curBaseTarget * 11 / 10;
      if (twofoldCurBaseTarget < 0) {
        twofoldCurBaseTarget = Constants.MAX_BASE_TARGET;
      }
      if (newBaseTarget > twofoldCurBaseTarget) {
        newBaseTarget = twofoldCurBaseTarget;
      }
      block.setBaseTarget(newBaseTarget);
      block.setCumulativeDifficulty(previousBlock.getCumulativeDifficulty().add(Convert.two64.divide(BigInteger.valueOf(newBaseTarget))));
    } else {
      Block itBlock = previousBlock;
      BigInteger avgBaseTarget = BigInteger.valueOf(itBlock.getCapacityBaseTarget());
      int blockCounter = 1;
      do {
        int previousHeight = itBlock.getHeight();
        itBlock = downloadCache.getBlock(itBlock.getPreviousBlockId());
        if (itBlock == null) {
          throw new BlockOutOfOrderException("Previous block does no longer exist for block height " + previousHeight);
        }
        blockCounter++;
        avgBaseTarget = (avgBaseTarget.multiply(BigInteger.valueOf(blockCounter))
            .add(BigInteger.valueOf(itBlock.getCapacityBaseTarget())))
            .divide(BigInteger.valueOf(blockCounter + 1L));
      } while (blockCounter < 24);
      long difTime = (long) block.getTimestamp() - itBlock.getTimestamp();
      long targetTimespan = 24L * Constants.BURST_BLOCK_TIME;

      if (difTime < targetTimespan / 2) {
        difTime = targetTimespan / 2;
      }

      if (difTime > targetTimespan * 2) {
        difTime = targetTimespan * 2;
      }

      long curBaseTarget = previousBlock.getCapacityBaseTarget();
      long newBaseTarget = avgBaseTarget.multiply(BigInteger.valueOf(difTime))
          .divide(BigInteger.valueOf(targetTimespan)).longValue();

      if (newBaseTarget < 0 || newBaseTarget > Constants.MAX_BASE_TARGET) {
        newBaseTarget = Constants.MAX_BASE_TARGET;
      }

      if (newBaseTarget == 0) {
        newBaseTarget = 1;
      }

      if (newBaseTarget < curBaseTarget * 8 / 10) {
        newBaseTarget = curBaseTarget * 8 / 10;
      }

      if (newBaseTarget > curBaseTarget * 12 / 10) {
        newBaseTarget = curBaseTarget * 12 / 10;
      }
      
      long peerBaseTarget = block.getBaseTarget();
      block.setBaseTarget(newBaseTarget);
      BigInteger difficulty = Convert.two64.divide(BigInteger.valueOf(newBaseTarget));
      
      if(Burst.getFluxCapacitor().getValue(FluxValues.POC_PLUS, block.getHeight())) {
        block.setCommitment(generator.estimateCommitment(block.getGeneratorId(), previousBlock));

        // update the average commitment based on a moving average filter
        long curCommitment = previousBlock.getAverageCommitment();
        
        long newAvgCommitment = (curCommitment*23L + block.getCommitment())/24L;
        // avoid changing more than 20% in a single block
        if (newAvgCommitment < curCommitment * 8 / 10) {
          newAvgCommitment = curCommitment * 8 / 10;
        }
        if (newAvgCommitment > curCommitment * 12 / 10) {
          newAvgCommitment = curCommitment * 12 / 10;
        }
        
        // assuming a minimum value of 1 BURST
        newAvgCommitment = Math.max(newAvgCommitment, Constants.ONE_BURST);
        block.setBaseTarget(newBaseTarget, newAvgCommitment);
        
        if(block.getPeer()!=null && peerBaseTarget != 0L && peerBaseTarget != block.getBaseTarget()) {
          // peer sent the base target and we do not agree with it
          throw new RuntimeException("Peer base target " + peerBaseTarget + ", expected is " + block.getBaseTarget() + ", peer " +
              block.getPeer().getAnnouncedAddress());
        }
        
        Block pastBlock = blockchain.getBlockAtHeight(block.getHeight() - Constants.MAX_ROLLBACK);
        
        long pastAverageCommitment = pastBlock.getAverageCommitment();
        double commitmentFactor = generator.getCommitmentFactor(newAvgCommitment, pastAverageCommitment, block.getHeight());
        
        difficulty = BigInteger.valueOf((long)(difficulty.doubleValue()*commitmentFactor));
      }      
      
      block.setCumulativeDifficulty(previousBlock.getCumulativeDifficulty().add(difficulty));
    }
  }

  @Override
  public int getScoopNum(Block block) {
    return generator.calculateScoop(block.getGenerationSignature(), block.getHeight());
  }
}
