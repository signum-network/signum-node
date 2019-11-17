package brs.transaction.type.messaging

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.entity.TransactionDuplicationKey
import brs.objects.FluxValues
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.convert.toUnsignedString
import com.google.gson.JsonObject
import java.nio.ByteBuffer
import java.util.*

class AliasBuy(dp: DependencyProvider) : Messaging(dp) {
    override val subtype = SUBTYPE_MESSAGING_ALIAS_BUY

    override val description = "Alias Buy"

    override fun parseAttachment(
        buffer: ByteBuffer,
        transactionVersion: Byte
    ): Attachment.MessagingAliasBuy {
        return Attachment.MessagingAliasBuy(dp, buffer, transactionVersion)
    }

    override fun parseAttachment(attachmentData: JsonObject) = Attachment.MessagingAliasBuy(dp, attachmentData)

    override fun applyAttachment(
        transaction: Transaction,
        senderAccount: Account,
        recipientAccount: Account
    ) {
        val attachment = transaction.attachment as Attachment.MessagingAliasBuy
        val aliasName = attachment.aliasName
        dp.aliasService.changeOwner(transaction.senderId, aliasName, transaction.blockTimestamp)
    }

    override fun getDuplicationKey(transaction: Transaction): TransactionDuplicationKey {
        val attachment = transaction.attachment as Attachment.MessagingAliasBuy
        // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
        return TransactionDuplicationKey(
            AliasAssignment::class,
            attachment.aliasName.toLowerCase(Locale.ENGLISH)
        )
    }

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        if (!dp.fluxCapacitorService.getValue(FluxValues.DIGITAL_GOODS_STORE, height)) {
            throw BurstException.NotYetEnabledException("Alias transfer not yet enabled at height $height")
        }
    }

    override fun validateAttachment(transaction: Transaction) {
        val attachment = transaction.attachment as Attachment.MessagingAliasBuy
        val aliasName = attachment.aliasName
        val alias = dp.aliasService.getAlias(aliasName)
        if (alias == null) {
            throw BurstException.NotCurrentlyValidException("Alias hasn't been registered yet: $aliasName")
        } else if (alias.accountId != transaction.recipientId) {
            throw BurstException.NotCurrentlyValidException("Alias is owned by account other than recipient: " + alias.accountId.toUnsignedString())
        }
        val offer = dp.aliasService.getOffer(alias)
            ?: throw BurstException.NotCurrentlyValidException("Alias is not for sale: $aliasName")
        if (transaction.amountPlanck < offer.pricePlanck) {
            val msg = ("Price is too low for: " + aliasName + " ("
                    + transaction.amountPlanck + " < " + offer.pricePlanck + ")")
            throw BurstException.NotCurrentlyValidException(msg)
        }
        if (offer.buyerId != 0L && offer.buyerId != transaction.senderId) {
            throw BurstException.NotCurrentlyValidException("Wrong buyer for " + aliasName + ": " + transaction.senderId.toUnsignedString() + " expected: " + offer.buyerId.toUnsignedString())
        }
    }

    override fun hasRecipient() = true
}
