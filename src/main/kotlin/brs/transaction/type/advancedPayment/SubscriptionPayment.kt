package brs.transaction.type.advancedPayment

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.entity.TransactionDuplicationKey
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class SubscriptionPayment(dp: DependencyProvider) : AdvancedPayment(dp) {
    override val subtype = SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT
    override val description = "Subscription Payment"
    override val isSigned = false
    override fun parseAttachment(buffer: ByteBuffer, transactionVersion: Byte) =
        Attachment.AdvancedPaymentSubscriptionPayment(dp, buffer, transactionVersion)

    override fun parseAttachment(attachmentData: JsonObject) =
        Attachment.AdvancedPaymentSubscriptionPayment(dp, attachmentData)

    override fun applyAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) = false
    override fun applyAttachment(transaction: Transaction, senderAccount: Account, recipientAccount: Account) = Unit
    override fun undoAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) = Unit
    override fun getDuplicationKey(transaction: Transaction) = TransactionDuplicationKey.IS_ALWAYS_DUPLICATE
    override fun preValidateAttachment(transaction: Transaction, height: Int) =
        throw BurstException.NotValidException("Subscription payment never validates")
    override fun validateAttachment(transaction: Transaction) =
        throw BurstException.NotValidException("Subscription payment never validates")

    override fun hasRecipient() = true
}
