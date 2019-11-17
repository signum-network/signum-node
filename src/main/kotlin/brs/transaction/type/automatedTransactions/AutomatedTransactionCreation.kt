package brs.transaction.type.automatedTransactions

import brs.at.AT
import brs.at.AtException
import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.objects.Constants
import brs.objects.FluxValues
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.byteArray.isZero
import brs.util.convert.toUnsignedString
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class AutomatedTransactionCreation(dp: DependencyProvider) : AutomatedTransactions(dp) {
    override val subtype = SUBTYPE_AT_CREATION
    override val description = "AT Creation"

    override fun parseAttachment(buffer: ByteBuffer, transactionVersion: Byte) =
        Attachment.AutomatedTransactionsCreation(dp, buffer, transactionVersion)

    override fun parseAttachment(attachmentData: JsonObject) =
        Attachment.AutomatedTransactionsCreation(dp, attachmentData)

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        if (transaction.amountPlanck != 0L) {
            throw BurstException.NotValidException("Invalid automated transaction transaction")
        }
        if (!dp.fluxCapacitorService.getValue(
                FluxValues.AUTOMATED_TRANSACTION_BLOCK,
                height
            )
        ) {
            throw BurstException.NotYetEnabledException("Automated Transactions not yet enabled at height $height")
        }
        val attachment = transaction.attachment as Attachment.AutomatedTransactionsCreation
        val totalPages: Long
        try {
            totalPages = dp.atController.checkCreationBytes(attachment.creationBytes, height).toLong()
        } catch (e: AtException) {
            throw BurstException.NotCurrentlyValidException("Invalid AT creation bytes", e)
        }

        val requiredFee = totalPages * dp.atConstants.costPerPage(height)
        if (transaction.feePlanck < requiredFee) {
            throw BurstException.NotValidException("Insufficient fee for AT creation. Minimum: " + (requiredFee / Constants.ONE_BURST).toUnsignedString())
        }
        if (dp.fluxCapacitorService.getValue(FluxValues.AT_FIX_BLOCK_3, height)) {
            if (attachment.name!!.length > Constants.MAX_AUTOMATED_TRANSACTION_NAME_LENGTH) {
                throw BurstException.NotValidException("Name of automated transaction over size limit")
            }
            if (attachment.description!!.length > Constants.MAX_AUTOMATED_TRANSACTION_DESCRIPTION_LENGTH) {
                throw BurstException.NotValidException("Description of automated transaction over size limit")
            }
        }
    }

    override fun validateAttachment(transaction: Transaction) {
        if (transaction.signature != null && dp.accountService.getAccount(transaction.id) != null) {
            val existingAccount = dp.accountService.getAccount(transaction.id)
                ?: throw BurstException.NotValidException("Account with transaction's ID does not exist")
            if (existingAccount.publicKey != null && !existingAccount.publicKey!!.isZero())
                throw BurstException.NotValidException("Account with id already exists")
        }
    }

    override fun applyAttachment(transaction: Transaction, senderAccount: Account, recipientAccount: Account) {
        val attachment = transaction.attachment as Attachment.AutomatedTransactionsCreation
        AT.addAT(
            dp,
            transaction.id,
            transaction.senderId,
            attachment.name.orEmpty(),
            attachment.description.orEmpty(),
            attachment.creationBytes,
            transaction.height
        )
    }

    override fun hasRecipient() = false
}
