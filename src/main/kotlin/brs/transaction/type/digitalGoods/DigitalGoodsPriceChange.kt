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

class DigitalGoodsPriceChange(dp: DependencyProvider) : DigitalGoods(dp) {
    override val subtype = SUBTYPE_DIGITAL_GOODS_PRICE_CHANGE
    override val description = "Price Change"
    override fun parseAttachment(buffer: ByteBuffer, transactionVersion: Byte) =
        Attachment.DigitalGoodsPriceChange(dp, buffer, transactionVersion)

    override fun parseAttachment(attachmentData: JsonObject) = Attachment.DigitalGoodsPriceChange(dp, attachmentData)

    override fun applyAttachment(transaction: Transaction, senderAccount: Account, recipientAccount: Account) {
        val attachment = transaction.attachment as Attachment.DigitalGoodsPriceChange
        dp.digitalGoodsStoreService.changePrice(attachment.goodsId, attachment.pricePlanck)
    }

    override fun doPreValidateAttachment(transaction: Transaction, height: Int) {
        // Nothing to pre-validate.
    }

    override fun validateAttachment(transaction: Transaction) {
        val attachment = transaction.attachment as Attachment.DigitalGoodsPriceChange
        val goods = dp.digitalGoodsStoreService.getGoods(attachment.goodsId)
        if (attachment.pricePlanck <= 0 || attachment.pricePlanck > Constants.MAX_BALANCE_PLANCK
            || goods != null && transaction.senderId != goods.sellerId
        ) {
            throw BurstException.NotValidException("Invalid digital goods price change: " + attachment.jsonObject.toJsonString())
        }
        if (goods == null || goods.isDelisted) {
            throw BurstException.NotCurrentlyValidException(
                "Goods ${attachment.goodsId.toUnsignedString()} not yet listed or already delisted"
            )
        }
    }

    override fun getDuplicationKey(transaction: Transaction): TransactionDuplicationKey {
        val attachment = transaction.attachment as Attachment.DigitalGoodsPriceChange
        // not a bug, uniqueness is based on DigitalGoods.DELISTING
        return TransactionDuplicationKey(DigitalGoodsDelisting::class, attachment.goodsId.toUnsignedString())
    }

    override fun hasRecipient() = false
}
