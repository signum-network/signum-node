package brs.transaction.type.advancedPayment

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.entity.TransactionDuplicationKey
import brs.objects.Constants
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class SubscriptionSubscribe(dp: DependencyProvider) : AdvancedPayment(dp) {
    override val subtype = SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE
    override val description = "Subscription Subscribe"
    override fun parseAttachment(buffer: ByteBuffer, transactionVersion: Byte) =
        Attachment.AdvancedPaymentSubscriptionSubscribe(dp, buffer, transactionVersion)

    override fun parseAttachment(attachmentData: JsonObject) =
        Attachment.AdvancedPaymentSubscriptionSubscribe(dp, attachmentData)

    override fun applyAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) = true

    override fun applyAttachment(
        transaction: Transaction,
        senderAccount: Account,
        recipientAccount: Account
    ) {
        val attachment = transaction.attachment as Attachment.AdvancedPaymentSubscriptionSubscribe
        dp.subscriptionService.addSubscription(
            senderAccount,
            recipientAccount!!,
            transaction.id,
            transaction.amountPlanck,
            transaction.timestamp,
            attachment.frequency!!
        )
    }

    override fun undoAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) = Unit
    override fun getDuplicationKey(transaction: Transaction) = TransactionDuplicationKey.IS_NEVER_DUPLICATE

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        val attachment = transaction.attachment as Attachment.AdvancedPaymentSubscriptionSubscribe
        if (attachment.frequency == null ||
            attachment.frequency < Constants.BURST_SUBSCRIPTION_MIN_FREQUENCY ||
            attachment.frequency > Constants.BURST_SUBSCRIPTION_MAX_FREQUENCY
        ) {
            throw BurstException.NotValidException("Invalid subscription frequency")
        }
        if (transaction.amountPlanck < Constants.ONE_BURST || transaction.amountPlanck > Constants.MAX_BALANCE_PLANCK) {
            throw BurstException.NotValidException("Subscriptions must be at least one burst")
        }
        if (transaction.senderId == transaction.recipientId) {
            throw BurstException.NotValidException("Cannot create subscription to same address")
        }
    }

    override fun validateAttachment(transaction: Transaction) {
        // Nothing to validate.
    }

    override fun hasRecipient() = true
}
