package brs.services.impl;

import brs.Account;
import brs.Block;
import brs.Blockchain;
import brs.BlockchainProcessor;
import brs.BlockchainProcessor.BlockOutOfOrderException;
import brs.Constants;
import brs.Generator;
import brs.Signum;
import brs.Transaction;
import brs.crypto.Crypto;
import brs.fluxcapacitor.FluxValues;
import brs.props.Props;
import brs.services.AccountService;
import brs.services.BlockService;
import brs.services.TransactionService;
import brs.util.Convert;
import brs.util.DownloadCacheImpl;
import brs.util.ThreadPool;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import signum.net.NetworkParameters;

//TODO: Create JavaDocs and remove this
@SuppressWarnings({ "checkstyle:MissingJavadocTypeCheck", "checkstyle:MissingJavadocMethodCheck" })

public class BlockServiceImpl implements BlockService {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final Blockchain blockchain;
    private final DownloadCacheImpl downloadCache;
    private final Generator generator;
    private NetworkParameters networkParameters;

    private final List<Block> watchedBlocks = new ArrayList<>();

    private static final Logger logger = LoggerFactory.getLogger(BlockServiceImpl.class);

    public BlockServiceImpl(
            AccountService accountService,
            TransactionService transactionService,
            Blockchain blockchain,
            DownloadCacheImpl downloadCache,
            Generator generator, NetworkParameters networkParameters) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.blockchain = blockchain;
        this.downloadCache = downloadCache;
        this.generator = generator;
        this.networkParameters = networkParameters;
    }

    @Override
    public boolean verifyBlockSignature(Block block)
            throws BlockchainProcessor.BlockOutOfOrderException {
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
            Account account = accountService.getAccount(publicKey);
            if (account != null) {
                if (Signum.getFluxCapacitor().getValue(FluxValues.PK_FREEZE2)
                        && account.getPublicKey() == null
                        && Signum.getBlockchain().getHeight() - account.getCreationHeight() > Signum
                                .getPropertyService().getInt(Props.PK_BLOCKS_PAST)) {
                    logger.error("Setting a new key for an old inactivated account");
                    return false;
                }
                // only if the account exists
                Account rewardAccount = getRewardAccount(block);
                publicKey = rewardAccount.getPublicKey();
            }

            return Crypto.verify(
                    block.getBlockSignature(),
                    data2,
                    publicKey,
                    block.getVersion() >= 3);

        } catch (RuntimeException e) {

            logger.info("Error verifying block signature", e);
            return false;

        }
    }

    @Override
    public boolean verifyGenerationSignature(final Block block)
            throws BlockchainProcessor.BlockNotAcceptedException {
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
            BigInteger ptime = generator.calculateDeadline(
                    hit,
                    previousBlock.getCapacityBaseTarget(),
                    block.getCommitment(), previousBlock.getAverageCommitment(), block.getHeight());
            if (BigInteger.valueOf(elapsedTime).compareTo(ptime) <= 0) {
                logger.info(
                        "Elapsed time {} should be higher than the deadline {}",
                        elapsedTime,
                        ptime);
            }
            return BigInteger.valueOf(elapsedTime).compareTo(ptime) > 0;
        } catch (RuntimeException e) {
            logger.info("Error verifying block generation signature", e);
            return false;
        }
    }

    private Account getRewardAccount(Block block) {
        Account rewardAccount = accountService.getAccount(block.getGeneratorPublicKey());
        if (rewardAccount.getPublicKey() == null) {
            rewardAccount.setPublicKey(block.getGeneratorPublicKey());
        }
        @SuppressWarnings("checkstyle:LineLengthCheck")
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
    public void preVerify(Block block, Block prevBlock)
            throws BlockchainProcessor.BlockNotAcceptedException, InterruptedException {
        preVerify(block, prevBlock, null);
    }

    @Override
    public void preVerify(Block block, Block prevBlock, byte[] scoopData)
            throws BlockchainProcessor.BlockNotAcceptedException, InterruptedException {
        // Just in case its already verified
        if (block.isVerified()) {
            return;
        }

        if (block.getPreviousBlockId() != prevBlock.getId()) {
            logger.info("Error pre-verifying block, invalid previous block");
            return;
        }

        int checkPointHeight = Signum.getPropertyService().getInt(Props.BRS_CHECKPOINT_HEIGHT);
        try {
            if (block.getHeight() < checkPointHeight) {
                // do not verify the nonce up to the checkpoint block
                block.setPocTime(BigInteger.valueOf(0L));
            } else {
                if (block.getHeight() == checkPointHeight) {
                    String checkPointHash = Signum.getPropertyService()
                            .getString(Props.BRS_CHECKPOINT_HASH);

                    String receivedHash = Hex.toHexString(block.getPreviousBlockHash());
                    if (!receivedHash.equals(checkPointHash)) {
                        logger.error("Error pre-verifying checkpoint block {}", block.getHeight());
                        return;
                    }
                    logger.info(
                            "Checkpoint block {} with previous block hash {} verified",
                            block.getHeight(),
                            Hex.toHexString(block.getPreviousBlockHash()));
                }
                // Pre-verify poc:
                if (scoopData == null) {
                    block.setPocTime(
                            generator.calculateHit(
                                    block.getGeneratorId(),
                                    block.getNonce(),
                                    block.getGenerationSignature(),
                                    getScoopNum(block),
                                    block.getHeight()));
                } else {
                    block.setPocTime(
                            generator.calculateHit(
                                    block.getGenerationSignature(),
                                    scoopData));
                }
            }
        } catch (RuntimeException e) {
            logger.info("Error pre-verifying block generation signature", e);
            return;
        }

        for (Transaction transaction : block.getTransactions()) {
            if (!transaction.verifySignature()) {
                if (logger.isInfoEnabled()) {
                    logger.info(
                            "Bad transaction signature during block pre-verification"
                                    + " for tx: {} at block height: {}",
                            Convert.toUnsignedLong(transaction.getId()), block.getHeight());
                }
                throw new BlockchainProcessor.TransactionNotAcceptedException(
                        "Invalid signature for tx: " + Convert.toUnsignedLong(transaction.getId())
                                + " at block height: " + block.getHeight(),
                        transaction);
            }
            if (Thread.currentThread().isInterrupted() || !ThreadPool.running.get()) {
                throw new InterruptedException();
            }
        }

    }

    @Override
    public void apply(Block block) {
        Account generatorAccount = accountService.getOrAddAccount(block.getGeneratorId());
        generatorAccount.apply(block.getGeneratorPublicKey(), block.getHeight());

        long blockReward = getBlockReward(block);
        long blockRewardTotal = blockReward;
        Map<Long, Integer> blockDistribution = networkParameters != null
                ? networkParameters.getBlockRewardDistribution(block.getHeight())
                : null;
        if (blockDistribution != null) {
            for (Long distAccountId : blockDistribution.keySet()) {
                Account distAccount = accountService.getOrAddAccount(distAccountId);
                if (distAccount != null) {
                    @SuppressWarnings("checkstyle:LineLengthCheck")
                    long distAmount = (blockRewardTotal * blockDistribution.get(distAccountId)) / 1000L;
                    blockReward -= distAmount;
                    accountService.addToBalanceAndUnconfirmedBalanceNQT(distAccount, distAmount);
                    accountService.addToForgedBalanceNQT(distAccount, distAmount);
                }
            }
        }
        if (!Signum.getFluxCapacitor().getValue(FluxValues.REWARD_RECIPIENT_ENABLE)) {
            accountService.addToBalanceAndUnconfirmedBalanceNQT(
                    generatorAccount,
                    block.getTotalFeeNqt() + blockReward);
            accountService.addToForgedBalanceNQT(
                    generatorAccount,
                    block.getTotalFeeNqt() + blockReward);
        } else {
            Account rewardAccount = getRewardAccount(block);

            long rewardFeesNqt = block.getTotalFeeNqt();
            if (Signum.getFluxCapacitor().getValue(FluxValues.SMART_FEES)) {
                rewardFeesNqt -= block.getTotalFeeCashBackNqt();
                rewardFeesNqt -= block.getTotalFeeBurntNqt();

                Account nullAccount = accountService.getOrAddAccount(0L);
                accountService.addToBalanceAndUnconfirmedBalanceNQT(
                        nullAccount,
                        block.getTotalFeeBurntNqt());
            }
            accountService.addToBalanceAndUnconfirmedBalanceNQT(
                    rewardAccount,
                    rewardFeesNqt + blockReward);
            accountService.addToForgedBalanceNQT(
                    rewardAccount,
                    rewardFeesNqt + blockReward);
        }

        for (Transaction transaction : block.getTransactions()) {
            transactionService.apply(transaction);
            if (networkParameters != null) {
                networkParameters.transactionApplied(transaction);
            }
        }
    }

    @Override
    public long getBlockReward(Block block) {
        return blockchain.getBlockReward(block.getHeight());
    }

    @Override
    public void setPrevious(Block block, Block previousBlock) {
        if (previousBlock != null) {
            if (previousBlock.getId() != block.getPreviousBlockId()) {
                // shouldn't happen as previous id is already verified, but just in case
                throw new IllegalStateException("Previous block id doesn't match");
            }
            block.setHeight(previousBlock.getHeight() + 1);
            if (block.getBaseTarget() == Constants.INITIAL_BASE_TARGET) {
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
    public void calculateBaseTarget(Block block, Block previousBlock)
            throws BlockOutOfOrderException {
        long blockTime = Signum.getFluxCapacitor().getValue(FluxValues.BLOCK_TIME);

        if (block.getPreviousBlockId() == 0 && block.getId() == Convert
                .parseUnsignedLong(Signum.getPropertyService().getString(Props.GENESIS_BLOCK_ID))) {
            block.setBaseTarget(Constants.INITIAL_BASE_TARGET);
            block.setCumulativeDifficulty(BigInteger.ZERO);
        } else if (block.getHeight() < 4) {
            block.setBaseTarget(Constants.INITIAL_BASE_TARGET);
            block.setCumulativeDifficulty(previousBlock.getCumulativeDifficulty()
                    .add(Convert.two64.divide(BigInteger.valueOf(Constants.INITIAL_BASE_TARGET))));
        } else if (block.getHeight() < Constants.SIGNUM_DIFF_ADJUST_CHANGE_BLOCK
                && !Signum.getFluxCapacitor().getValue(FluxValues.SODIUM)) {
            Block itBlock = previousBlock;
            BigInteger avgBaseTarget = BigInteger.valueOf(itBlock.getBaseTarget());
            do {
                itBlock = downloadCache.getBlock(itBlock.getPreviousBlockId());
                avgBaseTarget = avgBaseTarget.add(BigInteger.valueOf(itBlock.getBaseTarget()));
            } while (itBlock.getHeight() > block.getHeight() - 4);
            avgBaseTarget = avgBaseTarget.divide(BigInteger.valueOf(4));
            long difTime = (long) block.getTimestamp() - itBlock.getTimestamp();

            long curBaseTarget = avgBaseTarget.longValue();
            long newBaseTarget = BigInteger.valueOf(curBaseTarget)
                    .multiply(BigInteger.valueOf(difTime))
                    .divide(BigInteger.valueOf(blockTime * 4)).longValue();
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
            block.setCumulativeDifficulty(previousBlock.getCumulativeDifficulty()
                    .add(Convert.two64.divide(BigInteger.valueOf(newBaseTarget))));
        } else {
            Block itBlock = previousBlock;
            BigInteger avgBaseTarget = BigInteger.valueOf(itBlock.getCapacityBaseTarget());
            int blockCounter = 1;
            do {
                int previousHeight = itBlock.getHeight();
                if (previousHeight < 1) {
                    break;
                }
                itBlock = downloadCache.getBlock(itBlock.getPreviousBlockId());
                if (itBlock == null) {
                    throw new BlockOutOfOrderException(
                            "Previous block does no longer exist for block height "
                                    + previousHeight);
                }
                blockCounter++;
                avgBaseTarget = (avgBaseTarget.multiply(BigInteger.valueOf(blockCounter))
                        .add(BigInteger.valueOf(itBlock.getCapacityBaseTarget())))
                        .divide(BigInteger.valueOf(blockCounter + 1L));
            } while (blockCounter < 24);
            long difTime = (long) block.getTimestamp() - itBlock.getTimestamp();
            long targetTimespan = blockCounter * blockTime;

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

            if (Signum.getFluxCapacitor().getValue(FluxValues.POC_PLUS, block.getHeight())) {
                block.setCommitment(
                        generator.estimateCommitment(
                                block.getGeneratorId(),
                                previousBlock));

                // update the average commitment based on a moving average filter
                long curCommitment = previousBlock.getAverageCommitment();

                long avgCommitmentWindow = Signum.getFluxCapacitor()
                        .getValue(
                                FluxValues.AVERAGE_COMMITMENT_WINDOW,
                                block.getHeight());
                long newAvgCommitment = (curCommitment * (avgCommitmentWindow - 1L)
                        + block.getCommitment())
                        / avgCommitmentWindow;

                // avoid changing more than 20% in a single block
                if (newAvgCommitment < curCommitment * 8 / 10) {
                    newAvgCommitment = curCommitment * 8 / 10;
                }
                if (newAvgCommitment > curCommitment * 12 / 10) {
                    newAvgCommitment = curCommitment * 12 / 10;
                }

                // assuming a minimum value of 1 coin
                newAvgCommitment = Math.max(
                        newAvgCommitment,
                        Signum.getPropertyService().getInt(Props.ONE_COIN_NQT));
                block.setBaseTarget(newBaseTarget, newAvgCommitment);

                if (block.getPeer() != null
                        && peerBaseTarget != 0L
                        && peerBaseTarget != block.getBaseTarget()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                "Peer base target mismatch, height {},"
                                        + " id {}, generator id {}, cap bt {}, avg com. {}",
                                block.getHeight(),
                                Convert.toUnsignedLong(block.getId()),
                                Convert.toUnsignedLong(block.getGeneratorId()),
                                newBaseTarget, newAvgCommitment);
                    }
                    // peer sent the base target and we do not agree with it
                    throw new RuntimeException(
                            "Peer base target " + peerBaseTarget + ", expected is "
                                    + block.getBaseTarget() + ", peer "
                                    + block.getPeer().getAnnouncedAddress());
                }

                int pastBlockHeight = Math.max(0, block.getHeight() - Constants.MAX_ROLLBACK);
                Block pastBlock = blockchain.getBlockAtHeight(pastBlockHeight);

                long pastAverageCommitment = pastBlock.getAverageCommitment();
                if (Signum.getFluxCapacitor().getValue(FluxValues.SPEEDWAY, block.getHeight())) {
                    // use the average from past and now to get a smoother result
                    pastAverageCommitment = (pastAverageCommitment
                            + block.getAverageCommitment()) / 2;
                }
                double commitmentFactor = generator.getCommitmentFactor(
                        newAvgCommitment,
                        pastAverageCommitment,
                        block.getHeight());

                difficulty = BigInteger.valueOf(
                        (long) (difficulty.doubleValue() * commitmentFactor));
            }

            block.setCumulativeDifficulty(previousBlock.getCumulativeDifficulty().add(difficulty));
        }
    }

    @Override
    public int getScoopNum(Block block) {
        return generator.calculateScoop(block.getGenerationSignature(), block.getHeight());
    }
}
