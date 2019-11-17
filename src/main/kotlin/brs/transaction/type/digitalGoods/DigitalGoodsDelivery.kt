package brs.transaction.type.digitalGoods

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.entity.TransactionDuplicationKey
import brs.objects.Constants
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.convert.safeMultiply
import brs.util.convert.toUnsignedString
import brs.util.json.toJsonString
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class DigitalGoodsDelivery(dp: DependencyProvider) : DigitalGoods(dp) {
    override val subtype = SUBTYPE_DIGITAL_GOODS_DELIVERY
    override val description = "Delivery"
    override fun parseAttachment(buffer: ByteBuffer, transactionVersion: Byte) =
        Attachment.DigitalGoodsDelivery(dp, buffer, transactionVersion)

    override fun parseAttachment(attachmentData: JsonObject) = Attachment.DigitalGoodsDelivery(dp, attachmentData)

    override fun applyAttachment(transaction: Transaction, senderAccount: Account, recipientAccount: Account) {
        val attachment = transaction.attachment as Attachment.DigitalGoodsDelivery
        dp.digitalGoodsStoreService.deliver(transaction, attachment)
    }

    override fun doPreValidateAttachment(transaction: Transaction, height: Int) {
        // Nothing to pre-validate.
    }

    override fun validateAttachment(transaction: Transaction) {
        val attachment = transaction.attachment as Attachment.DigitalGoodsDelivery
        val purchase = dp.digitalGoodsStoreService.getPendingPurchase(attachment.purchaseId)
        if (attachment.goods.data.size > Constants.MAX_DGS_GOODS_LENGTH
            || attachment.goods.data.isEmpty()
            || attachment.goods.nonce.size != 32
            || attachment.discountPlanck < 0 || attachment.discountPlanck > Constants.MAX_BALANCE_PLANCK
            || purchase != null
            && (purchase.buyerId != transaction.recipientId
                    || transaction.senderId != purchase.sellerId
                    || attachment.discountPlanck > purchase.pricePlanck.safeMultiply(purchase.quantity.toLong()))
        ) {
            throw BurstException.NotValidException("Invalid digital goods delivery: " + attachment.jsonObject.toJsonString())
        }
        if (purchase == null || purchase.encryptedGoods != null) {
            throw BurstException.NotCurrentlyValidException("Purchase does not exist yet, or already delivered: " + attachment.jsonObject.toJsonString())
        }
    }

    override fun getDuplicationKey(transaction: Transaction): TransactionDuplicationKey {
        val attachment = transaction.attachment as Attachment.DigitalGoodsDelivery
        return TransactionDuplicationKey(
            DigitalGoodsDelivery::class,
            attachment.purchaseId.toUnsignedString()
        )
    }

    override fun hasRecipient() = true
}
