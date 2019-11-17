package brs.transaction.type.burstMining

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.entity.TransactionDuplicationKey
import brs.objects.FluxValues
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.convert.toUnsignedString
import brs.util.json.toJsonString
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class RewardRecipientAssignment(dp: DependencyProvider) : BurstMining(dp) {
    override val subtype = SUBTYPE_BURST_MINING_REWARD_RECIPIENT_ASSIGNMENT
    override val description = "Reward Recipient Assignment"

    override fun parseAttachment(
        buffer: ByteBuffer,
        transactionVersion: Byte
    ): Attachment.BurstMiningRewardRecipientAssignment {
        return Attachment.BurstMiningRewardRecipientAssignment(dp, buffer, transactionVersion)
    }

    override fun parseAttachment(attachmentData: JsonObject) =
        Attachment.BurstMiningRewardRecipientAssignment(dp, attachmentData)

    override fun applyAttachment(
        transaction: Transaction,
        senderAccount: Account,
        recipientAccount: Account
    ) {
        dp.accountService.setRewardRecipientAssignment(senderAccount, recipientAccount!!.id)
    }

    override fun getDuplicationKey(transaction: Transaction): TransactionDuplicationKey {
        return if (!dp.fluxCapacitorService.getValue(FluxValues.DIGITAL_GOODS_STORE)) {
            TransactionDuplicationKey.IS_NEVER_DUPLICATE // sync fails after 7007 without this TODO check this...
        } else TransactionDuplicationKey(
            RewardRecipientAssignment::class,
            transaction.senderId.toUnsignedString()
        )

    }

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        if (transaction.amountPlanck != 0L) {
            throw BurstException.NotValidException("Reward recipient assignment transaction must have 0 amount: " + transaction.toJsonObject().toJsonString())
        }
        if (!dp.fluxCapacitorService.getValue(FluxValues.REWARD_RECIPIENT_ENABLE, height)) {
            throw BurstException.NotCurrentlyValidException("Reward recipient assignment not allowed at height $height")
        }
    }

    override fun validateAttachment(transaction: Transaction) {
        val height = dp.blockchainService.lastBlock.height + 1
        val sender = dp.accountService.getAccount(transaction.senderId)
            ?: throw BurstException.NotCurrentlyValidException("Sender not yet known ?!")

        val rewardAssignment = dp.accountService.getRewardRecipientAssignment(sender)
        if (rewardAssignment != null && rewardAssignment.fromHeight >= height) {
            throw BurstException.NotCurrentlyValidException("Cannot reassign reward recipient before previous goes into effect: " + transaction.toJsonObject().toJsonString())
        }
        val recip = dp.accountService.getAccount(transaction.recipientId)
        if (recip?.publicKey == null) {
            throw BurstException.NotValidException("Reward recipient must have public key saved in blockchain: ${transaction.toJsonObject().toJsonString()}, account: $recip, account key height: ${recip?.keyHeight}")
        }
    }

    override fun hasRecipient() = true
}
