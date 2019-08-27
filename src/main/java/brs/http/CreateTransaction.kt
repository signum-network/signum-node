package brs.http

import brs.Account
import brs.Attachment
import brs.Burst
import brs.BurstException
import brs.fluxcapacitor.FluxValues
import com.google.gson.JsonElement

import javax.servlet.http.HttpServletRequest

import brs.Constants.FEE_QUANT
import brs.Constants.ONE_BURST
import brs.http.common.Parameters.*

internal abstract class CreateTransaction : APIServlet.JsonRequestHandler {

    private val apiTransactionManager: APITransactionManager

    constructor(apiTags: Array<APITag>, apiTransactionManager: APITransactionManager, replaceParameters: Boolean, vararg parameters: String) : super(apiTags, *if (replaceParameters) parameters else addCommonParameters(*parameters)) {
        this.apiTransactionManager = apiTransactionManager
    }

    constructor(apiTags: Array<APITag>, apiTransactionManager: APITransactionManager, vararg parameters: String) : super(apiTags, *addCommonParameters(*parameters)) {
        this.apiTransactionManager = apiTransactionManager
    }

    @Throws(BurstException::class)
    fun createTransaction(req: HttpServletRequest, senderAccount: Account, attachment: Attachment): JsonElement {
        return createTransaction(req, senderAccount, null, 0, attachment)
    }

    @Throws(BurstException::class)
    @JvmOverloads
    fun createTransaction(req: HttpServletRequest, senderAccount: Account, recipientId: Long?, amountNQT: Long, attachment: Attachment = Attachment.ORDINARY_PAYMENT): JsonElement {
        return apiTransactionManager.createTransaction(req, senderAccount, recipientId, amountNQT, attachment, minimumFeeNQT())
    }

    internal override fun requirePost(): Boolean {
        return true
    }

    private fun minimumFeeNQT(): Long {
        return if (Burst.getFluxCapacitor().getValue(FluxValues.PRE_DYMAXION)) FEE_QUANT else ONE_BURST
    }

    companion object {

        private val commonParameters = arrayOf(SECRET_PHRASE_PARAMETER, PUBLIC_KEY_PARAMETER, FEE_NQT_PARAMETER, DEADLINE_PARAMETER, REFERENCED_TRANSACTION_FULL_HASH_PARAMETER, BROADCAST_PARAMETER, MESSAGE_PARAMETER, MESSAGE_IS_TEXT_PARAMETER, MESSAGE_TO_ENCRYPT_PARAMETER, MESSAGE_TO_ENCRYPT_IS_TEXT_PARAMETER, ENCRYPTED_MESSAGE_DATA_PARAMETER, ENCRYPTED_MESSAGE_NONCE_PARAMETER, MESSAGE_TO_ENCRYPT_TO_SELF_PARAMETER, MESSAGE_TO_ENCRYPT_TO_SELF_IS_TEXT_PARAMETER, ENCRYPT_TO_SELF_MESSAGE_DATA, ENCRYPT_TO_SELF_MESSAGE_NONCE, RECIPIENT_PUBLIC_KEY_PARAMETER)

        private fun addCommonParameters(vararg parameters: String): Array<String> {
            return commonParameters + parameters
        }
    }

}