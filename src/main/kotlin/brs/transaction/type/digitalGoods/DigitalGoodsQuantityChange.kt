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
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class DigitalGoodsQuantityChange(dp: DependencyProvider) : DigitalGoods(dp) {
    override val subtype = SUBTYPE_DIGITAL_GOODS_QUANTITY_CHANGE
    override val description = "Quantity Change"
    override fun parseAttachment(buffer: ByteBuffer, transactionVersion: Byte) =
        Attachment.DigitalGoodsQuantityChange(dp, buffer, transactionVersion)

    override fun parseAttachment(attachmentData: JsonObject) = Attachment.DigitalGoodsQuantityChange(dp, attachmentData)

    override fun applyAttachment(transaction: Transaction, senderAccount: Account, recipientAccount: Account) {
        val attachment = transaction.attachment as Attachment.DigitalGoodsQuantityChange
        dp.digitalGoodsStoreService.changeQuantity(attachment.goodsId, attachment.deltaQuantity, false)
    }

    override fun doPreValidateAttachment(transaction: Transaction, height: Int) {
        // Nothing to pre-validate.
    }

    override fun validateAttachment(transaction: Transaction) {
        val attachment = transaction.attachment as Attachment.DigitalGoodsQuantityChange
        val goods = dp.digitalGoodsStoreService.getGoods(attachment.goodsId)
        if (attachment.deltaQuantity < -Constants.MAX_DGS_LISTING_QUANTITY
            || attachment.deltaQuantity > Constants.MAX_DGS_LISTING_QUANTITY
            || goods != null && transaction.senderId != goods.sellerId
        ) {
            throw BurstException.NotValidException("Invalid digital goods quantity change: " + attachment.jsonObject.toJsonString())
        }
        if (goods == null || goods.isDelisted) {
            throw BurstException.NotCurrentlyValidException("Goods ${attachment.goodsId.toUnsignedString()} not yet listed or already delisted")
        }
    }

    override fun getDuplicationKey(transaction: Transaction): TransactionDuplicationKey {
        val attachment = transaction.attachment as Attachment.DigitalGoodsQuantityChange
        // not a bug, uniqueness is based on DigitalGoods.DELISTING
        return TransactionDuplicationKey(DigitalGoodsDelisting::class, attachment.goodsId.toUnsignedString())
    }

    override fun hasRecipient() = false
}
