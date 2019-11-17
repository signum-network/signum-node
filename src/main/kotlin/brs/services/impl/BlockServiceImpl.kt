package brs.services.impl

import brs.entity.Account
import brs.entity.Block
import brs.entity.DependencyProvider
import brs.objects.Constants
import brs.objects.FluxValues
import brs.objects.Genesis
import brs.services.BlockService
import brs.services.BlockchainProcessorService
import brs.services.BlockchainProcessorService.BlockOutOfOrderException
import brs.util.crypto.Crypto
import brs.util.crypto.verifySignature
import brs.util.logging.safeDebug
import brs.util.logging.safeInfo
import brs.util.logging.safeWarn
import org.slf4j.LoggerFactory
import java.math.BigInteger

class BlockServiceImpl(private val dp: DependencyProvider) : BlockService {
    override fun verifyBlockSignature(block: Block): Boolean {
        try {
            if (block.blockSignature == null) {
                return false
            }
            val previousBlock = dp.blockchainService.getBlock(block.previousBlockId)
                ?: throw BlockOutOfOrderException("Can't verify signature because previous block is missing")

            val data = block.toBytes()
            val data2 = ByteArray(data.size - 64) // TODO copyOfRange
            System.arraycopy(data, 0, data2, 0, data2.size)

            val publicKey: ByteArray?
            val genAccount = dp.accountService.getAccount(block.generatorPublicKey)
            val rewardAssignment: Account.RewardRecipientAssignment?
            rewardAssignment =
                if (genAccount == null) null else dp.accountService.getRewardRecipientAssignment(genAccount)
            publicKey = if (genAccount == null || rewardAssignment == null || !dp.fluxCapacitorService.getValue(
                    FluxValues.REWARD_RECIPIENT_ENABLE
                )
            ) {
                block.generatorPublicKey
            } else {
                if (previousBlock.height + 1 >= rewardAssignment.fromHeight) {
                    dp.accountService.getAccount(rewardAssignment.recipientId)!!.publicKey
                } else {
                    dp.accountService.getAccount(rewardAssignment.prevRecipientId)!!.publicKey
                }
            }

            return data2.verifySignature(block.blockSignature!!, publicKey!!, block.version >= 3)

        } catch (e: RuntimeException) {

            logger.safeInfo(e) { "Error verifying block signature" }
            return false

        }

    }

    override fun verifyGenerationSignature(block: Block): Boolean {
        try {
            val previousBlock = dp.blockchainService.getBlock(block.previousBlockId)
                ?: throw BlockOutOfOrderException(
                    "Can't verify generation signature because previous block is missing"
                )

            val correctGenerationSignature = dp.generatorService.calculateGenerationSignature(
                previousBlock.generationSignature, previousBlock.generatorId
            )
            if (!block.generationSignature.contentEquals(correctGenerationSignature)) {
                return false
            }
            val elapsedTime = block.timestamp - previousBlock.timestamp
            // TODO pocTime is accessed outside of lock.
            val pTime = block.pocTime!!.divide(BigInteger.valueOf(previousBlock.baseTarget))
            return BigInteger.valueOf(elapsedTime.toLong()) > pTime
        } catch (e: RuntimeException) {
            logger.safeInfo(e) { "Error verifying block generation signature" }
            return false
        }

    }

    override fun preVerify(block: Block, scoopData: ByteArray?, warnIfNotVerified: Boolean) {
        block.verificationLock.withLock {
            // Check if it's already verified
            if (block.verified) {
                return
            }

            if (warnIfNotVerified) {
                logger.safeWarn { "Block was not pre-verified!" }
            }

            dp.downloadCacheService.removeUnverified(block.id)

            try {
                // Pre-verify poc:
                if (scoopData == null) {
                    block.pocTime = dp.generatorService.calculateHit(
                        block.generatorId,
                        block.nonce, block.generationSignature, getScoopNum(block), block.height
                    )
                } else {
                    block.pocTime = dp.generatorService.calculateHit(
                        block.generatorId,
                        block.nonce, block.generationSignature, scoopData
                    )
                }
            } catch (e: RuntimeException) {
                logger.safeInfo(e) { "Error pre-verifying block generation signature" }
                return
            }

            val feeArray = LongArray(block.transactions.size)
            val sha256 = Crypto.sha256()
            for ((slotIdx, transaction) in block.transactions.withIndex()) {
                if (!transaction.verifySignature()) {
                    logger.safeInfo { "Bad transaction signature during block pre-verification for tx: ${transaction.stringId} at block height: ${block.height}" }
                    throw BlockchainProcessorService.TransactionNotAcceptedException(
                        "Invalid signature for tx " + transaction.stringId + " at block height: " + block.height,
                        transaction
                    )
                }
                dp.transactionService.preValidate(transaction, block.height)
                sha256.update(transaction.toBytes())
                feeArray[slotIdx] = transaction.feePlanck
            }

            if (dp.fluxCapacitorService.getValue(FluxValues.NEXT_FORK)) {
                feeArray.sort()
                for (i in feeArray.indices) {
                    if (feeArray[i] >= Constants.FEE_QUANT * (i + 1)) {
                        throw BlockchainProcessorService.BlockNotAcceptedException("Transaction fee is not enough to be included in this block " + block.height)
                    }
                }
            }

            if (!block.payloadHash.contentEquals(sha256.digest())) {
                throw BlockchainProcessorService.BlockNotAcceptedException("Payload hash doesn't match for block " + block.height)
            }

            logger.safeDebug { "Pre-verified block at height ${block.height}"}

            block.verified = true
        }
    }

    override fun apply(block: Block) {
        val generatorAccount = dp.accountService.getOrAddAccount(block.generatorId)
        generatorAccount.apply(dp, block.generatorPublicKey, block.height)
        if (!dp.fluxCapacitorService.getValue(FluxValues.REWARD_RECIPIENT_ENABLE)) {
            dp.accountService.addToBalanceAndUnconfirmedBalancePlanck(
                generatorAccount,
                block.totalFeePlanck + getBlockReward(block)
            )
            dp.accountService.addToForgedBalancePlanck(generatorAccount, block.totalFeePlanck + getBlockReward(block))
        } else {
            val rewardAccount: Account
            val rewardAssignment = dp.accountService.getRewardRecipientAssignment(generatorAccount)
            rewardAccount = when {
                rewardAssignment == null -> generatorAccount
                block.height >= rewardAssignment.fromHeight -> dp.accountService.getAccount(rewardAssignment.recipientId)!!
                else -> dp.accountService.getAccount(rewardAssignment.prevRecipientId)!!
            }
            dp.accountService.addToBalanceAndUnconfirmedBalancePlanck(
                rewardAccount,
                block.totalFeePlanck + getBlockReward(block)
            )
            dp.accountService.addToForgedBalancePlanck(rewardAccount, block.totalFeePlanck + getBlockReward(block))
        }

        for (transaction in block.transactions) {
            dp.transactionService.apply(transaction)
        }
    }

    override fun getBlockReward(block: Block): Long {
        if (block.height == 0 || block.height >= 1944000) {
            return 0
        }
        val month = block.height / 10800
        return BigInteger.valueOf(10000).multiply(BigInteger.valueOf(95).pow(month))
            .divide(BigInteger.valueOf(100).pow(month)).toLong() * Constants.ONE_BURST
    }

    override fun setPrevious(block: Block, previousBlock: Block?) {
        if (previousBlock != null) {
            check(previousBlock.id == block.previousBlockId) {
                // shouldn't happen as previous id is already verified, but just in case
                "Previous block id doesn't match"
            }
            block.height = previousBlock.height + 1
            if (block.baseTarget == Constants.INITIAL_BASE_TARGET) {
                try {
                    this.calculateBaseTarget(block, previousBlock)
                } catch (e: BlockOutOfOrderException) {
                    throw IllegalStateException(e.toString(), e)
                }

            }
        } else {
            block.height = 0
        }
        block.transactions.forEach { transaction -> transaction.setBlock(block) }
    }

    override fun calculateBaseTarget(block: Block, previousBlock: Block) {
        if (block.id == Genesis.GENESIS_BLOCK_ID && block.previousBlockId == 0L) {
            block.baseTarget = Constants.INITIAL_BASE_TARGET
            block.cumulativeDifficulty = BigInteger.ZERO
        } else if (block.height < 4) {
            block.baseTarget = Constants.INITIAL_BASE_TARGET
            block.cumulativeDifficulty = previousBlock.cumulativeDifficulty.add(
                two64.divide(
                    BigInteger.valueOf(
                        Constants.INITIAL_BASE_TARGET
                    )
                )
            )
        } else if (block.height < Constants.BURST_DIFF_ADJUST_CHANGE_BLOCK) {
            var itBlock: Block? = previousBlock
            var avgBaseTarget = BigInteger.valueOf(itBlock!!.baseTarget)
            do {
                itBlock = dp.downloadCacheService.getBlock(itBlock!!.previousBlockId)
                avgBaseTarget = avgBaseTarget.add(BigInteger.valueOf(itBlock!!.baseTarget))
            } while (itBlock!!.height > block.height - 4)
            avgBaseTarget = avgBaseTarget.divide(BigInteger.valueOf(4))
            val difTime = block.timestamp.toLong() - itBlock.timestamp

            val curBaseTarget = avgBaseTarget.toLong()
            var newBaseTarget = BigInteger.valueOf(curBaseTarget).multiply(BigInteger.valueOf(difTime))
                .divide(BigInteger.valueOf(240L * 4)).toLong()
            if (newBaseTarget < 0 || newBaseTarget > Constants.MAX_BASE_TARGET) {
                newBaseTarget = Constants.MAX_BASE_TARGET
            }
            if (newBaseTarget < curBaseTarget * 9 / 10) {
                newBaseTarget = curBaseTarget * 9 / 10
            }
            if (newBaseTarget == 0L) {
                newBaseTarget = 1
            }
            var twofoldCurBaseTarget = curBaseTarget * 11 / 10
            if (twofoldCurBaseTarget < 0) {
                twofoldCurBaseTarget = Constants.MAX_BASE_TARGET
            }
            if (newBaseTarget > twofoldCurBaseTarget) {
                newBaseTarget = twofoldCurBaseTarget
            }
            block.baseTarget = newBaseTarget
            block.cumulativeDifficulty =
                previousBlock.cumulativeDifficulty.add(two64.divide(BigInteger.valueOf(newBaseTarget)))
        } else {
            var itBlock: Block? = previousBlock
            var avgBaseTarget = BigInteger.valueOf(itBlock!!.baseTarget)
            var blockCounter = 1
            do {
                val previousHeight = itBlock!!.height
                itBlock = dp.downloadCacheService.getBlock(itBlock.previousBlockId)
                if (itBlock == null) {
                    throw BlockOutOfOrderException("Previous block does no longer exist for block height $previousHeight")
                }
                blockCounter++
                avgBaseTarget = avgBaseTarget.multiply(BigInteger.valueOf(blockCounter.toLong()))
                    .add(BigInteger.valueOf(itBlock.baseTarget))
                    .divide(BigInteger.valueOf(blockCounter + 1L))
            } while (blockCounter < 24)
            var difTime = block.timestamp.toLong() - itBlock!!.timestamp
            val targetTimespan = 24L * 4 * 60

            if (difTime < targetTimespan / 2) {
                difTime = targetTimespan / 2
            }

            if (difTime > targetTimespan * 2) {
                difTime = targetTimespan * 2
            }

            val curBaseTarget = previousBlock.baseTarget
            var newBaseTarget = avgBaseTarget.multiply(BigInteger.valueOf(difTime))
                .divide(BigInteger.valueOf(targetTimespan)).toLong()

            if (newBaseTarget < 0 || newBaseTarget > Constants.MAX_BASE_TARGET) {
                newBaseTarget = Constants.MAX_BASE_TARGET
            }

            if (newBaseTarget == 0L) {
                newBaseTarget = 1
            }

            if (newBaseTarget < curBaseTarget * 8 / 10) {
                newBaseTarget = curBaseTarget * 8 / 10
            }

            if (newBaseTarget > curBaseTarget * 12 / 10) {
                newBaseTarget = curBaseTarget * 12 / 10
            }

            block.baseTarget = newBaseTarget
            block.cumulativeDifficulty =
                previousBlock.cumulativeDifficulty.add(two64.divide(BigInteger.valueOf(newBaseTarget)))
        }
    }

    override fun getScoopNum(block: Block): Int {
        return dp.generatorService.calculateScoop(block.generationSignature, block.height.toLong())
    }

    companion object {
        private val two64: BigInteger = BigInteger.valueOf(2).pow(64)
        private val logger = LoggerFactory.getLogger(BlockServiceImpl::class.java)
    }
}
