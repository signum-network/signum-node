package brs.transaction.type.payment

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.objects.FluxValues
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.convert.safeDivide
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class MultiOutSamePayment(dp: DependencyProvider) : Payment(dp) {
    override val subtype = SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_SAME_OUT
    override val description = "Multi-out Same Payment"

    override fun parseAttachment(
        buffer: ByteBuffer,
        transactionVersion: Byte
    ) = Attachment.PaymentMultiSameOutCreation(dp, buffer, transactionVersion)

    override fun parseAttachment(attachmentData: JsonObject) =
        Attachment.PaymentMultiSameOutCreation(dp, attachmentData)

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        if (!dp.fluxCapacitorService.getValue(FluxValues.PRE_DYMAXION, height)) {
            throw BurstException.NotCurrentlyValidException("Multi Same Out Payments are not allowed at height $height")
        }

        val attachment = transaction.attachment as Attachment.PaymentMultiSameOutCreation
        if (attachment.getRecipients().size < 2 && transaction.amountPlanck % attachment.getRecipients().size == 0L) {
            throw BurstException.NotValidException("Invalid multi out payment")
        }
    }

    override fun validateAttachment(transaction: Transaction) {
        // Nothing to validate.
    }

    override fun applyAttachment(
        transaction: Transaction,
        senderAccount: Account,
        recipientAccount: Account
    ) {
        val attachment = transaction.attachment as Attachment.PaymentMultiSameOutCreation
        val amountPlanck = transaction.amountPlanck.safeDivide(attachment.getRecipients().size.toLong())
        attachment.getRecipients().forEach { a ->
            dp.accountService.addToBalanceAndUnconfirmedBalancePlanck(
                dp.accountService.getOrAddAccount(a), amountPlanck
            )
        }
    }

    override fun hasRecipient() = false

    override fun parseAppendices(builder: Transaction.Builder, attachmentData: JsonObject) = Unit

    override fun parseAppendices(
        builder: Transaction.Builder,
        flags: Int,
        version: Byte,
        buffer: ByteBuffer
    ) = Unit
}
