package brs.transaction.type.coloredCoins

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
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

class AssetTransfer(dp: DependencyProvider) : ColoredCoins(dp) {
    override val subtype = SUBTYPE_COLORED_COINS_ASSET_TRANSFER
    override val description = "Asset Transfer"

    override fun parseAttachment(
        buffer: ByteBuffer,
        transactionVersion: Byte
    ): Attachment.ColoredCoinsAssetTransfer {
        return Attachment.ColoredCoinsAssetTransfer(dp, buffer, transactionVersion)
    }

    override fun parseAttachment(attachmentData: JsonObject) = Attachment.ColoredCoinsAssetTransfer(dp, attachmentData)

    override fun applyAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account): Boolean {
        logger.safeTrace { "TransactionType ASSET_TRANSFER" }
        val attachment = transaction.attachment as Attachment.ColoredCoinsAssetTransfer
        val unconfirmedAssetBalance =
            dp.accountService.getUnconfirmedAssetBalanceQuantity(senderAccount, attachment.assetId)
        if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.quantity) {
            dp.accountService.addToUnconfirmedAssetBalanceQuantity(
                senderAccount,
                attachment.assetId,
                -attachment.quantity
            )
            return true
        }
        return false
    }

    override fun applyAttachment(
        transaction: Transaction,
        senderAccount: Account,
        recipientAccount: Account
    ) {
        val attachment = transaction.attachment as Attachment.ColoredCoinsAssetTransfer
        dp.accountService.addToAssetBalanceQuantity(senderAccount, attachment.assetId, -attachment.quantity)
        dp.accountService.addToAssetAndUnconfirmedAssetBalanceQuantity(
            recipientAccount,
            attachment.assetId,
            attachment.quantity
        )
        dp.assetExchangeService.addAssetTransfer(transaction, attachment)
    }

    override fun undoAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) {
        val attachment = transaction.attachment as Attachment.ColoredCoinsAssetTransfer
        dp.accountService.addToUnconfirmedAssetBalanceQuantity(
            senderAccount,
            attachment.assetId,
            attachment.quantity
        )
    }

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        val attachment = transaction.attachment as Attachment.ColoredCoinsAssetTransfer
        if (transaction.amountPlanck != 0L
            || attachment.comment != null && attachment.comment.length > Constants.MAX_ASSET_TRANSFER_COMMENT_LENGTH
            || attachment.assetId == 0L
        ) {
            throw BurstException.NotValidException("Invalid asset transfer amount or comment: " + attachment.jsonObject.toJsonString())
        }
        if (transaction.version > 0 && attachment.comment != null) {
            throw BurstException.NotValidException("Asset transfer comments no longer allowed, use message " + "or encrypted message appendix instead")
        }
    }

    override fun validateAttachment(transaction: Transaction) {
        val attachment = transaction.attachment as Attachment.ColoredCoinsAssetTransfer
        val asset = dp.assetExchangeService.getAsset(attachment.assetId)
            ?: throw BurstException.NotCurrentlyValidException(
                "Asset " + attachment.assetId.toUnsignedString() +
                        " does not exist yet"
            )
        if (attachment.quantity <= 0 || attachment.quantity > asset.quantity) {
            throw BurstException.NotValidException("Invalid asset transfer asset or quantity: " + attachment.jsonObject.toJsonString())
        }
    }

    override fun hasRecipient() = true

    companion object {
        val logger: Logger = LoggerFactory.getLogger(AssetTransfer::class.java)
    }
}
