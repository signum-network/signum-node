package brs.transaction.type.advancedPayment

import brs.entity.*
import brs.objects.Constants
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.convert.safeAdd
import brs.util.convert.safeMultiply
import brs.util.logging.safeTrace
import com.google.gson.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class EscrowCreation(dp: DependencyProvider) : AdvancedPayment(dp) {
    override val subtype = SUBTYPE_ADVANCED_PAYMENT_ESCROW_CREATION
    override val description = "Escrow Creation"

    override fun parseAttachment(
        buffer: ByteBuffer,
        transactionVersion: Byte
    ): Attachment.AdvancedPaymentEscrowCreation {
        return Attachment.AdvancedPaymentEscrowCreation(dp, buffer, transactionVersion)
    }

    override fun parseAttachment(attachmentData: JsonObject) =
        Attachment.AdvancedPaymentEscrowCreation(dp, attachmentData)

    override fun applyAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account): Boolean {
        logger.safeTrace { "TransactionType ESCROW_CREATION" }
        val totalAmountPlanck = calculateAttachmentTotalAmountPlanck(transaction)
        if (senderAccount.unconfirmedBalancePlanck < totalAmountPlanck) {
            return false
        }
        dp.accountService.addToUnconfirmedBalancePlanck(senderAccount, -totalAmountPlanck)
        return true
    }

    public override fun calculateAttachmentTotalAmountPlanck(transaction: Transaction): Long {
        val attachment = transaction.attachment as Attachment.AdvancedPaymentEscrowCreation
        return attachment.amountPlanck.safeAdd(attachment.totalSigners.toLong().safeMultiply(Constants.ONE_BURST))
    }

    override fun applyAttachment(
        transaction: Transaction,
        senderAccount: Account,
        recipientAccount: Account
    ) {
        val attachment = transaction.attachment as Attachment.AdvancedPaymentEscrowCreation
        val totalAmountPlanck = calculateAttachmentTotalAmountPlanck(transaction)
        dp.accountService.addToBalancePlanck(senderAccount, -totalAmountPlanck)
        val signers = attachment.getSigners()
        signers.forEach { signer ->
            dp.accountService.addToBalanceAndUnconfirmedBalancePlanck(
                dp.accountService.getOrAddAccount(
                    signer
                ), Constants.ONE_BURST
            )
        }
        dp.escrowService.addEscrowTransaction(
            senderAccount,
            recipientAccount!!,
            transaction.id,
            attachment.amountPlanck,
            attachment.getRequiredSigners(),
            attachment.getSigners(),
            transaction.timestamp + attachment.deadline,
            attachment.deadlineAction!!
        )
    }

    override fun undoAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) {
        dp.accountService.addToUnconfirmedBalancePlanck(
            senderAccount,
            calculateAttachmentTotalAmountPlanck(transaction)
        )
    }

    override fun getDuplicationKey(transaction: Transaction): TransactionDuplicationKey {
        return TransactionDuplicationKey.IS_NEVER_DUPLICATE
    }

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        val attachment = transaction.attachment as Attachment.AdvancedPaymentEscrowCreation
        var totalAmountPlanck = attachment.amountPlanck.safeAdd(transaction.feePlanck)
        if (transaction.senderId == transaction.recipientId) {
            throw BurstException.NotValidException("Escrow must have different sender and recipient")
        }
        totalAmountPlanck = totalAmountPlanck.safeAdd(attachment.totalSigners * Constants.ONE_BURST)
        if (transaction.amountPlanck != 0L) {
            throw BurstException.NotValidException("Transaction sent amount must be 0 for escrow")
        }
        if (totalAmountPlanck < 0L || totalAmountPlanck > Constants.MAX_BALANCE_PLANCK) {
            throw BurstException.NotValidException("Invalid escrow creation amount")
        }
        if (transaction.feePlanck < Constants.ONE_BURST) {
            throw BurstException.NotValidException("Escrow transaction must have a fee at least 1 burst")
        }
        if (attachment.getRequiredSigners() < 1 || attachment.getRequiredSigners() > 10) {
            throw BurstException.NotValidException("Escrow required signers much be 1 - 10")
        }
        if (attachment.getRequiredSigners() > attachment.totalSigners) {
            throw BurstException.NotValidException("Cannot have more required than signers on escrow")
        }
        if (attachment.totalSigners < 1 || attachment.totalSigners > 10) {
            throw BurstException.NotValidException("Escrow transaction requires 1 - 10 signers")
        }
        if (attachment.deadline < 1 || attachment.deadline > 7776000) { // max deadline 3 months
            throw BurstException.NotValidException("Escrow deadline must be 1 - 7776000 seconds")
        }
        if (attachment.deadlineAction == Escrow.DecisionType.UNDECIDED) {
            throw BurstException.NotValidException("Invalid deadline action for escrow")
        }
        if (attachment.getSigners().contains(transaction.senderId) || attachment.getSigners().contains(
                transaction.recipientId
            )
        ) {
            throw BurstException.NotValidException("Escrow sender and recipient cannot be signers")
        }
    }

    override fun validateAttachment(transaction: Transaction) {
        // Nothing to validate.
    }

    override fun hasRecipient() = true

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(EscrowCreation::class.java)
    }
}
