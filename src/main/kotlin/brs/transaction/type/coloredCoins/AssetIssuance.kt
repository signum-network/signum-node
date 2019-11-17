package brs.transaction.type.coloredCoins

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.objects.Constants
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.json.toJsonString
import brs.util.string.isInAlphabet
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class AssetIssuance(dp: DependencyProvider) : ColoredCoins(dp) {
    override val subtype = SUBTYPE_COLORED_COINS_ASSET_ISSUANCE

    override val description = "Asset Issuance"

    public override fun getBaselineFee(height: Int) = BASELINE_ASSET_ISSUANCE_FEE

    override fun parseAttachment(
        buffer: ByteBuffer,
        transactionVersion: Byte
    ): Attachment.ColoredCoinsAssetIssuance {
        return Attachment.ColoredCoinsAssetIssuance(dp, buffer, transactionVersion)
    }

    override fun parseAttachment(attachmentData: JsonObject) = Attachment.ColoredCoinsAssetIssuance(dp, attachmentData)

    override fun applyAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) = true

    override fun applyAttachment(
        transaction: Transaction,
        senderAccount: Account,
        recipientAccount: Account
    ) {
        val attachment = transaction.attachment as Attachment.ColoredCoinsAssetIssuance
        val assetId = transaction.id
        dp.assetExchangeService.addAsset(transaction, attachment)
        dp.accountService.addToAssetAndUnconfirmedAssetBalanceQuantity(
            senderAccount,
            assetId,
            attachment.quantity
        )
    }

    override fun undoAttachmentUnconfirmed(transaction: Transaction, senderAccount: Account) {
        // Nothing to undo
    }

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        val attachment = transaction.attachment as Attachment.ColoredCoinsAssetIssuance
        if (attachment.name.length < Constants.MIN_ASSET_NAME_LENGTH
            || attachment.name.length > Constants.MAX_ASSET_NAME_LENGTH
            || attachment.description.length > Constants.MAX_ASSET_DESCRIPTION_LENGTH
            || attachment.decimals < 0 || attachment.decimals > 8
            || attachment.quantity <= 0
            || attachment.quantity > Constants.MAX_ASSET_QUANTITY
        ) {
            throw BurstException.NotValidException("Invalid asset issuance: " + attachment.jsonObject.toJsonString())
        }
        if (!attachment.name.isInAlphabet()) {
            throw BurstException.NotValidException("Invalid asset name: " + attachment.name)
        }
    }

    override fun validateAttachment(transaction: Transaction) {
        // Nothing to validate.
    }

    override fun hasRecipient() = false
}
