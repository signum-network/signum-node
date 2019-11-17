package brs.transaction.type.digitalGoods

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.objects.Constants
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.json.toJsonString
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class DigitalGoodsListing(dp: DependencyProvider) : DigitalGoods(dp) {
    override val subtype = SUBTYPE_DIGITAL_GOODS_LISTING
    override val description = "Listing"
    override fun parseAttachment(buffer: ByteBuffer, transactionVersion: Byte) =
        Attachment.DigitalGoodsListing(dp, buffer, transactionVersion)

    override fun parseAttachment(attachmentData: JsonObject) = Attachment.DigitalGoodsListing(dp, attachmentData)

    override fun applyAttachment(transaction: Transaction, senderAccount: Account, recipientAccount: Account) {
        val attachment = transaction.attachment as Attachment.DigitalGoodsListing
        dp.digitalGoodsStoreService.listGoods(transaction, attachment)
    }

    override fun doPreValidateAttachment(transaction: Transaction, height: Int) {
        val attachment = transaction.attachment as Attachment.DigitalGoodsListing
        if (attachment.name!!.isEmpty()
            || attachment.name.length > Constants.MAX_DGS_LISTING_NAME_LENGTH
            || attachment.description!!.length > Constants.MAX_DGS_LISTING_DESCRIPTION_LENGTH
            || attachment.tags!!.length > Constants.MAX_DGS_LISTING_TAGS_LENGTH
            || attachment.quantity < 0 || attachment.quantity > Constants.MAX_DGS_LISTING_QUANTITY
            || attachment.pricePlanck <= 0 || attachment.pricePlanck > Constants.MAX_BALANCE_PLANCK
        ) {
            throw BurstException.NotValidException("Invalid digital goods listing: " + attachment.jsonObject.toJsonString())
        }
    }

    override fun validateAttachment(transaction: Transaction) {
        // Nothing to validate.
    }

    override fun hasRecipient() = false
}
