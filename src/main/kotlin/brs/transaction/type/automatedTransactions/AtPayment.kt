package brs.transaction.type.automatedTransactions

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class AtPayment(dp: DependencyProvider) : AutomatedTransactions(dp) {
    override val subtype = SUBTYPE_AT_PAYMENT
    override val description = "AT Payment"
    override val isSigned = false
    override fun parseAttachment(buffer: ByteBuffer, transactionVersion: Byte) = Attachment.AtPayment(dp)
    override fun parseAttachment(attachmentData: JsonObject) = Attachment.AtPayment(dp)
    override fun preValidateAttachment(transaction: Transaction, height: Int) =
        throw BurstException.NotValidException("AT payment never validates")

    override fun validateAttachment(transaction: Transaction) =
        throw BurstException.NotValidException("AT payment never validates")

    override fun applyAttachment(transaction: Transaction, senderAccount: Account, recipientAccount: Account) = Unit
    override fun hasRecipient() = true
}
