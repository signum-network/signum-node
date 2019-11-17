package brs.transaction.type.coloredCoins

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.convert.safeMultiply
import brs.util.convert.toUnsignedString
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class BidOrderCancellation(dp: DependencyProvider) : OrderCancellation(dp) {

    override val subtype = SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION

    override val description = "Bid Order Cancellation"

    override fun parseAttachment(
        buffer: ByteBuffer,
        transactionVersion: Byte
    ): Attachment.ColoredCoinsBidOrderCancellation {
        return Attachment.ColoredCoinsBidOrderCancellation(dp, buffer, transactionVersion)
    }

    override fun parseAttachment(attachmentData: JsonObject) =
        Attachment.ColoredCoinsBidOrderCancellation(dp, attachmentData)

    override fun applyAttachment(
        transaction: Transaction,
        senderAccount: Account,
        recipientAccount: Account
    ) {
        val attachment = transaction.attachment as Attachment.ColoredCoinsBidOrderCancellation
        val order = dp.assetExchangeService.getBidOrder(attachment.orderId)
        dp.assetExchangeService.removeBidOrder(attachment.orderId)
        if (order != null) dp.accountService.addToUnconfirmedBalancePlanck(
            senderAccount,
            order.quantity.safeMultiply(order.pricePlanck)
        )
    }

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        // Nothing to pre-validate.
    }

    override fun validateAttachment(transaction: Transaction) {
        val attachment = transaction.attachment as Attachment.ColoredCoinsBidOrderCancellation
        val bid = dp.assetExchangeService.getBidOrder(attachment.orderId)
            ?: throw BurstException.NotCurrentlyValidException("Invalid bid order: " + attachment.orderId.toUnsignedString())
        if (bid.accountId != transaction.senderId) {
            throw BurstException.NotValidException("Order " + attachment.orderId.toUnsignedString() + " was created by account " + bid.accountId.toUnsignedString())
        }
    }
}
