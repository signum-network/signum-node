package brs.transaction.type.payment

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.objects.Constants
import brs.objects.FluxValues
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class MultiOutPayment(dp: DependencyProvider) : Payment(dp) {
    override val subtype = SUBTYPE_PAYMENT_ORDINARY_PAYMENT_MULTI_OUT
    override val description = "Multi-out payment"

    override fun parseAttachment(
        buffer: ByteBuffer,
        transactionVersion: Byte
    ) = Attachment.PaymentMultiOutCreation(dp, buffer, transactionVersion)

    override fun parseAttachment(attachmentData: JsonObject) = Attachment.PaymentMultiOutCreation(dp, attachmentData)

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        if (!dp.fluxCapacitorService.getValue(FluxValues.PRE_DYMAXION, height)) {
            throw BurstException.NotCurrentlyValidException("Multi Out Payments are not allowed at height $height")
        }

        val attachment = transaction.attachment as Attachment.PaymentMultiOutCreation
        val amountPlanck = attachment.amountPlanck
        if (amountPlanck <= 0
            || amountPlanck >= Constants.MAX_BALANCE_PLANCK
            || amountPlanck != transaction.amountPlanck
            || attachment.getRecipients().size < 2
        ) {
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
        val attachment = transaction.attachment as Attachment.PaymentMultiOutCreation
        for (recipient in attachment.getRecipients()) {
            dp.accountService.addToBalanceAndUnconfirmedBalancePlanck(
                dp.accountService.getOrAddAccount(
                    recipient[0]
                ), recipient[1]
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