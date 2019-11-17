package brs.transaction.type.digitalGoods

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.objects.Constants
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.convert.safeMultiply
import brs.util.convert.toUnsignedString
import brs.util.json.toJsonString
import brs.util.logging.safeTrace
import com.google.gson.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class DigitalGoodsPurchase(dp: DependencyProvider) : DigitalGoods(dp) {
    override val subtype = SUBTYPE_DIGITAL_GOODS_PURCHASE
    override val description = "Purchase"
    override fun parseAttachment(buffer: ByteBuffer, transactionVersion: Byte) =
        Attachment.DigitalGoodsPurchase(dp, buffer, transactionVersion)

    override fun parseAttachment(attachmentData: JsonObject) = Attachment.DigitalGoodsPurchase(dp, attachmentData)

    override fun applyAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account): Boolean {
        logger.safeTrace { "TransactionType PURCHASE" }
        val totalAmountPlanck = calculateAttachmentTotalAmountPlanck(transaction)
        if (senderAccount.unconfirmedBalancePlanck >= totalAmountPlanck) {
            dp.accountService.addToUnconfirmedBalancePlanck(senderAccount, -totalAmountPlanck)
            return true
        }
        return false
    }

    public override fun calculateAttachmentTotalAmountPlanck(transaction: Transaction): Long {
        val attachment = transaction.attachment as Attachment.DigitalGoodsPurchase
        return attachment.quantity.toLong().safeMultiply(attachment.pricePlanck)
    }

    override fun undoAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) {
        dp.accountService.addToUnconfirmedBalancePlanck(
            senderAccount,
            calculateAttachmentTotalAmountPlanck(transaction)
        )
    }

    override fun applyAttachment(transaction: Transaction, senderAccount: Account, recipientAccount: Account) {
        val attachment = transaction.attachment as Attachment.DigitalGoodsPurchase
        dp.digitalGoodsStoreService.purchase(transaction, attachment)
    }

    override fun doPreValidateAttachment(transaction: Transaction, height: Int) {
        if (transaction.encryptedMessage != null && !transaction.encryptedMessage.isText) {
            throw BurstException.NotValidException("Only text encrypted messages allowed")
        }
    }

    override fun validateAttachment(transaction: Transaction) {
        val attachment = transaction.attachment as Attachment.DigitalGoodsPurchase
        val goods = dp.digitalGoodsStoreService.getGoods(attachment.goodsId)
        if (attachment.quantity <= 0 || attachment.quantity > Constants.MAX_DGS_LISTING_QUANTITY
            || attachment.pricePlanck <= 0 || attachment.pricePlanck > Constants.MAX_BALANCE_PLANCK
            || goods != null && goods.sellerId != transaction.recipientId
        ) {
            throw BurstException.NotValidException("Invalid digital goods purchase: " + attachment.jsonObject.toJsonString())
        }
        if (goods == null || goods.isDelisted) {
            throw BurstException.NotCurrentlyValidException(
                "Goods " + attachment.goodsId.toUnsignedString() +
                        "not yet listed or already delisted"
            )
        }
        if (attachment.quantity > goods.quantity || attachment.pricePlanck != goods.pricePlanck) {
            throw BurstException.NotCurrentlyValidException("Goods price or quantity changed: " + attachment.jsonObject.toJsonString())
        }
        if (attachment.deliveryDeadlineTimestamp <= dp.blockchainService.lastBlock.timestamp) {
            throw BurstException.NotCurrentlyValidException("Delivery deadline has already expired: " + attachment.deliveryDeadlineTimestamp)
        }
    }

    override fun hasRecipient() = true

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DigitalGoodsPurchase::class.java)
    }
}
