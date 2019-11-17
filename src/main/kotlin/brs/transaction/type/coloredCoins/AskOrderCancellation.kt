package brs.transaction.type.coloredCoins

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.convert.toUnsignedString
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class AskOrderCancellation(dp: DependencyProvider) : OrderCancellation(dp) {
    override val subtype = SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION
    override val description = "Ask Order Cancellation"

    override fun parseAttachment(
        buffer: ByteBuffer,
        transactionVersion: Byte
    ): Attachment.ColoredCoinsAskOrderCancellation {
        return Attachment.ColoredCoinsAskOrderCancellation(dp, buffer, transactionVersion)
    }

    override fun parseAttachment(attachmentData: JsonObject): Attachment.ColoredCoinsAskOrderCancellation {
        return Attachment.ColoredCoinsAskOrderCancellation(dp, attachmentData)
    }

    override fun applyAttachment(
        transaction: Transaction,
        senderAccount: Account,
        recipientAccount: Account
    ) {
        val attachment = transaction.attachment as Attachment.ColoredCoinsAskOrderCancellation
        val order = dp.assetExchangeService.getAskOrder(attachment.orderId)
        dp.assetExchangeService.removeAskOrder(attachment.orderId)
        if (order != null) {
            dp.accountService.addToUnconfirmedAssetBalanceQuantity(
                senderAccount,
                order.assetId,
                order.quantity
            )
        }
    }

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        // Nothing to pre-validate.
    }

    override fun validateAttachment(transaction: Transaction) {
        val attachment = transaction.attachment as Attachment.ColoredCoinsAskOrderCancellation
        val ask = dp.assetExchangeService.getAskOrder(attachment.orderId)
            ?: throw BurstException.NotCurrentlyValidException("Invalid ask order: " + attachment.orderId.toUnsignedString())
        if (ask.accountId != transaction.senderId) {
            throw BurstException.NotValidException("Order " + attachment.orderId.toUnsignedString() + " was created by account " + ask.accountId.toUnsignedString())
        }
    }
}
