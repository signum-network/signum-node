package brs.transaction.type.messaging

import brs.entity.Account
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.objects.Constants
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.convert.toBytes
import brs.util.json.toJsonString
import com.google.gson.JsonObject
import java.nio.ByteBuffer

class AccountInfo(dp: DependencyProvider) : Messaging(dp) {

    override val subtype = SUBTYPE_MESSAGING_ACCOUNT_INFO

    override val description = "Account Info"

    override fun parseAttachment(
        buffer: ByteBuffer,
        transactionVersion: Byte
    ): Attachment.MessagingAccountInfo {
        return Attachment.MessagingAccountInfo(dp, buffer, transactionVersion)
    }

    override fun parseAttachment(attachmentData: JsonObject) = Attachment.MessagingAccountInfo(dp, attachmentData)

    override fun preValidateAttachment(transaction: Transaction, height: Int) {
        val attachment = transaction.attachment as Attachment.MessagingAccountInfo
        if (attachment.name.toBytes().size > Constants.MAX_ACCOUNT_NAME_LENGTH || attachment.description.toBytes().size > Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH
        ) {
            throw BurstException.NotValidException("Invalid account info issuance: " + attachment.jsonObject.toJsonString())
        }
    }

    override fun validateAttachment(transaction: Transaction) {
        // Nothing to validate
    }

    override fun applyAttachment(
        transaction: Transaction,
        senderAccount: Account,
        recipientAccount: Account
    ) {
        val attachment = transaction.attachment as Attachment.MessagingAccountInfo
        dp.accountService.setAccountInfo(senderAccount, attachment.name, attachment.description)
    }

    override fun hasRecipient() = false
}
