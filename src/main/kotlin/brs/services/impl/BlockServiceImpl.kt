package brs.services.impl

import brs.entity.Account
import brs.entity.Block
import brs.entity.DependencyProvider
import brs.objects.Constants
import brs.objects.FluxValues
import brs.objects.Genesis
import brs.objects.Props
import brs.services.BlockService
import brs.services.BlockchainProcessorService
import brs.services.BlockchainProcessorService.BlockOutOfOrderException
import brs.util.BurstException
import brs.util.biginteger.*
import brs.util.crypto.Crypto
import brs.util.crypto.verifySignature
import brs.util.logging.safeDebug
import brs.util.logging.safeInfo
import org.bouncycastle.util.encoders.Hex
import org.slf4j.LoggerFactory
import java.math.BigInteger

class BlockServiceImpl(private val dp: DependencyProvider) : BlockService {
    override fun verifyBlockSignature(block: Block): Boolean {
        try {
            if (block.blockSignature == null) {
                return false
            }
            val previousBlock = dp.blockchainService.getBlock(block.previousBlockId) ?: throw BlockOutOfOrderException("Can't verify signature because previous block is missing")

            val data = block.toBytes(includeSignature = false)

            val genAccount = dp.accountService.getAccount(block.generatorPublicKey)
            val rewardAssignment = if (genAccount == null) null else dp.accountService.getRewardRecipientAssignment(genAccount)
            val publicKey =
                if (genAccount == null || rewardAssignment == null || !dp.fluxCapacitorService.getValue(FluxValues.REWARD_RECIPIENT_ENABLE)) {
                    block.generatorPublicKey
                } else {
                    if (previousBlock.height + 1 >= rewardAssignment.fromHeight) {
                        dp.accountService.getAccount(rewardAssignment.recipientId)?.publicKey
                    } else {
                        dp.accountService.getAccount(rewardAssignment.prevRecipientId)?.publicKey
                    }
                } ?: throw BurstException.NotValidException("Could not get signer's public key to verify block")

            return data.verifySignature(block.blockSignature!!, publicKey, block.version >= 3)
        } catch (e: Exception) {
            logger.safeInfo(e) { "Error verifying block signature" }
            return false
        }
    }

    override fun verifyGenerationSignature(block: Block, pocTime: BigInteger): Boolean {
        try {
            val previousBlock = dp.downloadCacheService.getBlock(block.previousBlockId) ?: throw BlockOutOfOrderException("Can't verify generation signature because previous block is missing")
            val correctGenerationSignature = dp.generatorService.calculateGenerationSignature(previousBlock.generationSignature, previousBlock.generatorId)
            if (!block.generationSignature.contentEquals(correctGenerationSignature)) return false
            return (block.timestamp - previousBlock.timestamp) > (pocTime / previousBlock.baseTarget)
        } catch (e: Exception) {
            logger.safeInfo(e) { "Error verifying block generation signature" }
            return false
        }
    }

    // TODO:
    // When the importer thread fails to push a block it clears the cache and may even rollback.
    // This causes the pre-verifier threads to fail spectacularly and print loads of errors.
    // We need to either hide the errors caused by this, or find a way to pause / restart the
    // pre-verifier threads whenever the download cache gets cleared.
    override fun preVerify(block: Block, scoopData: ByteArray?, warnIfNotVerified: Boolean) {
        block.preVerificationLock.withLock {
            // Check if it's already verified
            if (block.preVerified) {
                if (!warnIfNotVerified) {
                    logger.safeDebug { "Thread ${Thread.currentThread().name}: Block height ${block.height} already verified" }
                }
                return
            }

            if (warnIfNotVerified) {
                logger.safeDebug { "Block at height ${block.height} was not pre-verified! Pre-verification threads are probably not keeping up..." }
            }

            val checkPointHeight = dp.propertyService.get(if (dp.propertyService.get(Props.DEV_TESTNET)) Props.DEV_CHECKPOINT_HEIGHT else Props.BRS_CHECKPOINT_HEIGHT)
            val pocTime = try {
                if(block.height < checkPointHeight){
                    // do not verify the nonce up to the checkpoint block (lowers CPU usage)
                    BigInteger.valueOf(0L)
                }
                else {
                    if(block.height == checkPointHeight) {
                        val checkPointHash =
                            dp.propertyService.get(if (dp.propertyService.get(Props.DEV_TESTNET)) Props.DEV_CHECKPOINT_HASH else Props.BRS_CHECKPOINT_HASH)

                        val receivedHash = Hex.toHexString(block.previousBlockHash)
                        if (!receivedHash.equals(checkPointHash)) {
                            throw BlockchainProcessorService.BlockNotAcceptedException("Error pre-verifying checkpoint block hash")
                        }
                    }
                    if (scoopData == null) {
                    dp.generatorService.calculateHit(block.generatorId, block.nonce, block.generationSignature, getScoopNum(block), block.height)
                    } else {
                        dp.generatorService.calculateHit(block.generationSignature, scoopData)
                    }
                }
            } catch (e: Exception) {
                throw BlockchainProcessorService.BlockNotAcceptedException("Error pre-verifying block generation signature", e)
            }

            if (!dp.blockService.verifyGenerationSignature(block, pocTime)) {
                throw BlockchainProcessorService.BlockNotAcceptedException("Generation signature verification failed for block ${block.height}")
            }

            val feeArray = LongArray(block.transactions.size)
            val sha256 = Crypto.sha256()
            for ((slotIdx, transaction) in block.transactions.withIndex()) {
                if (!transaction.verifySignature()) {
                    logger.safeInfo { "Bad transaction signature during block pre-verification for tx: ${transaction.stringId} at block height: ${block.height}" }
                    throw BlockchainProcessorService.TransactionNotAcceptedException("Invalid signature for tx " + transaction.stringId + " at block height: " + block.height, transaction)
                }
                dp.transactionService.preValidate(transaction, block.height)
                sha256.update(transaction.toBytes())
                feeArray[slotIdx] = transaction.feePlanck
            }

            if (dp.fluxCapacitorService.getValue(FluxValues.NEXT_FORK)) {
                feeArray.sort()
                for (i in feeArray.indices) {
                    if (feeArray[i] < Constants.FEE_QUANT * (i + 1)) {
                        throw BlockchainProcessorService.BlockNotAcceptedException("Transaction fee is not enough to be included in this block at height ${block.height}")
                    }
                }
            }

            if (!block.payloadHash.contentEquals(sha256.digest())) {
                throw BlockchainProcessorService.BlockNotAcceptedException("Payload hash doesn't match for block ${block.height}")
            }

            logger.safeDebug { "Thread ${Thread.currentThread().name} pre-verified block at height ${block.height}" }

            block.preVerified = true
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
        return (10000 * (95 pow month) / (100 pow month)).toLong() * Constants.ONE_BURST
}

    override fun setPrevious(block: Block, previousBlock: Block?) {
        if (previousBlock != null) {
            check(previousBlock.id == block.previousBlockId) {
                // shouldn't happen as previous id is already verified, but just in case
                "Previous block id doesn't match"
            }
            block.height = previousBlock.height + 1
            if (block.baseTarget == Constants.MAX_BASE_TARGET) {
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
        when {
            block.id == Genesis.BLOCK_ID && block.previousBlockId == 0L -> {
                block.baseTarget = Constants.MAX_BASE_TARGET
                block.cumulativeDifficulty = BigInteger.ZERO
            }
            block.height < 4 -> {
                block.baseTarget = Constants.MAX_BASE_TARGET
                block.cumulativeDifficulty = previousBlock.cumulativeDifficulty + (two64 / Constants.MAX_BASE_TARGET)
            }
            block.height < Constants.BURST_DIFF_ADJUST_CHANGE_BLOCK -> {
                var itBlock: Block = previousBlock
                var avgBaseTarget = itBlock.baseTarget.toBigInteger()
                do {
                    itBlock = dp.downloadCacheService.getBlock(itBlock.previousBlockId) ?: throw BlockOutOfOrderException("Previous block does no longer exist for block height ${itBlock.height}")
                    avgBaseTarget += itBlock.baseTarget
                } while (itBlock.height > block.height - 4)
                avgBaseTarget /= 4
                val difTime = block.timestamp.toLong() - itBlock.timestamp
                val curBaseTarget = avgBaseTarget.toLong()
                var newBaseTarget = (avgBaseTarget * difTime / 960).toLong()
                if (newBaseTarget < 0 || newBaseTarget > Constants.MAX_BASE_TARGET) newBaseTarget = Constants.MAX_BASE_TARGET
                if (newBaseTarget < curBaseTarget * 9 / 10) newBaseTarget = curBaseTarget * 9 / 10
                if (newBaseTarget == 0L) newBaseTarget = 1
                var twofoldCurBaseTarget = curBaseTarget * 11 / 10
                if (twofoldCurBaseTarget < 0) twofoldCurBaseTarget = Constants.MAX_BASE_TARGET
                if (newBaseTarget > twofoldCurBaseTarget) newBaseTarget = twofoldCurBaseTarget
                block.baseTarget = newBaseTarget
                block.cumulativeDifficulty = previousBlock.cumulativeDifficulty + (two64 / newBaseTarget)
            }
            else -> {
                var itBlock: Block = previousBlock
                var avgBaseTarget = itBlock.baseTarget.toBigInteger()
                var blockCounter = 1
                do {
                    itBlock = dp.downloadCacheService.getBlock(itBlock.previousBlockId) ?: throw BlockOutOfOrderException("Previous block does no longer exist for block height ${itBlock.height}")
                    blockCounter++
                    avgBaseTarget = (avgBaseTarget * blockCounter + itBlock.baseTarget) / (blockCounter + 1L)
                } while (blockCounter < 24)
                var difTime = block.timestamp.toLong() - itBlock.timestamp
                val targetTimespan = 24L * 4 * 60
                if (difTime < targetTimespan / 2) difTime = targetTimespan / 2
                if (difTime > targetTimespan * 2) difTime = targetTimespan * 2
                val curBaseTarget = previousBlock.baseTarget
                var newBaseTarget = (avgBaseTarget * difTime / targetTimespan).toLong()
                if (newBaseTarget < 0 || newBaseTarget > Constants.MAX_BASE_TARGET) newBaseTarget = Constants.MAX_BASE_TARGET
                if (newBaseTarget == 0L) newBaseTarget = 1
                if (newBaseTarget < curBaseTarget * 8 / 10) newBaseTarget = curBaseTarget * 8 / 10
                if (newBaseTarget > curBaseTarget * 12 / 10) newBaseTarget = curBaseTarget * 12 / 10
                block.baseTarget = newBaseTarget
                block.cumulativeDifficulty = previousBlock.cumulativeDifficulty + (two64 / newBaseTarget)
            }
        }
    }

    override fun getScoopNum(block: Block): Int {
        return dp.generatorService.calculateScoop(block.generationSignature, block.height.toLong())
    }

    companion object {
        private val two64: BigInteger = 2.pow(64)
        private val logger = LoggerFactory.getLogger(BlockServiceImpl::class.java)
    }
}
