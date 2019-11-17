package brs.transaction.type.digitalGoods

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.entity.TransactionDuplicationKey
import brs.objects.Constants
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.convert.toUnsignedString
import brs.util.json.toJsonString
import brs.util.logging.safeTrace
import com.google.gson.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class DigitalGoodsRefund(dp: DependencyProvider) : DigitalGoods(dp) {
    override val subtype = SUBTYPE_DIGITAL_GOODS_REFUND
    override val description = "Refund"
    override fun parseAttachment(buffer: ByteBuffer, transactionVersion: Byte) =
        Attachment.DigitalGoodsRefund(dp, buffer, transactionVersion)

    override fun parseAttachment(attachmentData: JsonObject) = Attachment.DigitalGoodsRefund(dp, attachmentData)

    override fun applyAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account): Boolean {
        logger.safeTrace { "TransactionType REFUND" }
        val totalAmountPlanck = calculateAttachmentTotalAmountPlanck(transaction)
        if (senderAccount.unconfirmedBalancePlanck >= totalAmountPlanck) {
            dp.accountService.addToUnconfirmedBalancePlanck(senderAccount, -totalAmountPlanck)
            return true
        }
        return false
    }

    public override fun calculateAttachmentTotalAmountPlanck(transaction: Transaction): Long {
        val attachment = transaction.attachment as Attachment.DigitalGoodsRefund
        return attachment.refundPlanck
    }

    override fun undoAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) {
        dp.accountService.addToUnconfirmedBalancePlanck(
            senderAccount,
            calculateAttachmentTotalAmountPlanck(transaction)
        )
    }

    override fun applyAttachment(transaction: Transaction, senderAccount: Account, recipientAccount: Account) {
        val attachment = transaction.attachment as Attachment.DigitalGoodsRefund
        dp.digitalGoodsStoreService.refund(
            transaction.senderId,
            attachment.purchaseId,
            attachment.refundPlanck,
            transaction.encryptedMessage
        )
    }

    override fun doPreValidateAttachment(transaction: Transaction, height: Int) {
        if (transaction.encryptedMessage != null && !transaction.encryptedMessage.isText) {
            throw BurstException.NotValidException("Only text encrypted messages allowed")
        }
    }

    override fun validateAttachment(transaction: Transaction) {
        val attachment = transaction.attachment as Attachment.DigitalGoodsRefund
        val purchase = dp.digitalGoodsStoreService.getPurchase(attachment.purchaseId)
        if (attachment.refundPlanck < 0 || attachment.refundPlanck > Constants.MAX_BALANCE_PLANCK
            || purchase != null && (purchase.buyerId != transaction.recipientId || transaction.senderId != purchase.sellerId)
        ) {
            throw BurstException.NotValidException("Invalid digital goods refund: " + attachment.jsonObject.toJsonString())
        }
        if (purchase?.encryptedGoods == null || purchase.refundPlanck != 0L) {
            throw BurstException.NotCurrentlyValidException("Purchase does not exist or is not delivered or is already refunded")
        }
    }

    override fun getDuplicationKey(transaction: Transaction): TransactionDuplicationKey {
        val attachment = transaction.attachment as Attachment.DigitalGoodsRefund
        return TransactionDuplicationKey(DigitalGoodsRefund::class, attachment.purchaseId.toUnsignedString())
    }

    override fun hasRecipient() = true

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DigitalGoodsRefund::class.java)
    }
}
