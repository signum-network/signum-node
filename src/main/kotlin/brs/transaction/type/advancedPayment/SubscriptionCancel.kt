package brs.transaction.type.advancedPayment

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.entity.TransactionDuplicationKey
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.convert.toUnsignedString
import brs.util.logging.safeTrace
import com.google.gson.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class SubscriptionCancel(dp: DependencyProvider) : AdvancedPayment(dp) {
    override val subtype = SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_CANCEL
    override val description = "Subscription Cancel"

    override fun parseAttachment(buffer: ByteBuffer, transactionVersion: Byte) =
        Attachment.AdvancedPaymentSubscriptionCancel(dp, buffer, transactionVersion)

    override fun parseAttachment(attachmentData: JsonObject) =
        Attachment.AdvancedPaymentSubscriptionCancel(dp, attachmentData)

    override fun applyAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account): Boolean {
        logger.safeTrace { "TransactionType SUBSCRIPTION_CANCEL" }
        val attachment = transaction.attachment as Attachment.AdvancedPaymentSubscriptionCancel
        dp.subscriptionService.addRemoval(attachment.subscriptionId)
        return true
    }

    override fun applyAttachment(transaction: Transaction, senderAccount: Account, recipientAccount: Account) {
        val attachment = transaction.attachment as Attachment.AdvancedPaymentSubscriptionCancel
        dp.subscriptionService.removeSubscription(attachment.subscriptionId)
    }

    override fun undoAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) = Unit

    override fun getDuplicationKey(transaction: Transaction): TransactionDuplicationKey {
        val attachment = transaction.attachment as Attachment.AdvancedPaymentSubscriptionCancel
        return TransactionDuplicationKey(
            SubscriptionCancel::class,
            attachment.subscriptionId.toUnsignedString()
        )
    }

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        // Nothing to pre-validate.
    }

    override fun validateAttachment(transaction: Transaction) {
        val attachment = transaction.attachment as Attachment.AdvancedPaymentSubscriptionCancel
        val subscription = dp.subscriptionService.getSubscription(attachment.subscriptionId)!!
        if (subscription.senderId != transaction.senderId && subscription.recipientId != transaction.senderId) {
            throw BurstException.NotValidException("Subscription cancel can only be done by participants")
        }
    }

    override fun hasRecipient() = false

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(SubscriptionCancel::class.java)
    }
}
