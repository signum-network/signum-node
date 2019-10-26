package brs.api.http

import brs.transaction.appendix.Attachment
import brs.objects.Constants
import brs.entity.DependencyProvider
import brs.api.http.common.Parameters.BROADCAST_PARAMETER
import brs.api.http.common.Parameters.DEADLINE_PARAMETER
import brs.api.http.common.Parameters.FEE_PLANCK_PARAMETER
import brs.api.http.common.Parameters.PUBLIC_KEY_PARAMETER
import brs.api.http.common.Parameters.RECIPIENTS_PARAMETER
import brs.api.http.common.Parameters.REFERENCED_TRANSACTION_FULL_HASH_PARAMETER
import brs.api.http.common.Parameters.SECRET_PHRASE_PARAMETER
import brs.api.http.common.ResultFields.ERROR_CODE_RESPONSE
import brs.api.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE
import brs.util.convert.emptyToNull
import brs.util.convert.parseUnsignedLong
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import javax.servlet.http.HttpServletRequest

internal class SendMoneyMulti(private val dp: DependencyProvider) : CreateTransaction(dp, arrayOf(APITag.TRANSACTIONS, APITag.CREATE_TRANSACTION), true, *commonParameters) {
    override fun processRequest(request: HttpServletRequest): JsonElement {
        val sender = dp.parameterService.getSenderAccount(request)
        val recipientString = request.getParameter(RECIPIENTS_PARAMETER).emptyToNull()

        if (recipientString == null) {
            val response = JsonObject()
            response.addProperty(ERROR_CODE_RESPONSE, 3)
            response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Recipients not specified")
            return response
        }

        val transactionArray = recipientString.split(";".toRegex(), Constants.MAX_MULTI_OUT_RECIPIENTS).toTypedArray()

        if (transactionArray.size > Constants.MAX_MULTI_OUT_RECIPIENTS || transactionArray.size < 2) {
            val response = JsonObject()
            response.addProperty(ERROR_CODE_RESPONSE, 4)
            response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid number of recipients")
            return response
        }

        val recipients = mutableMapOf<Long, Long>()

        var totalAmountPlanck: Long = 0
        try {
            for (transactionString in transactionArray) {
                val recipientArray = transactionString.split(":".toRegex(), 2).toTypedArray()
                val recipientId = recipientArray[0].parseUnsignedLong()
                val amountPlanck = recipientArray[1].parseUnsignedLong()
                recipients[recipientId] = amountPlanck
                totalAmountPlanck += amountPlanck
            }
        } catch (e: Exception) {
            val response = JsonObject()
            response.addProperty(ERROR_CODE_RESPONSE, 4)
            response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid recipients parameter")
            return response
        }

        if (sender.balancePlanck < totalAmountPlanck) {
            val response = JsonObject()
            response.addProperty(ERROR_CODE_RESPONSE, 6)
            response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Insufficient funds")
            return response
        }

        val attachment = Attachment.PaymentMultiOutCreation(dp, recipients, dp.blockchainService.height)

        return createTransaction(request, sender, null, attachment.amountPlanck, attachment)
    }

    companion object {
        private val commonParameters = arrayOf(SECRET_PHRASE_PARAMETER, PUBLIC_KEY_PARAMETER, FEE_PLANCK_PARAMETER, DEADLINE_PARAMETER, REFERENCED_TRANSACTION_FULL_HASH_PARAMETER, BROADCAST_PARAMETER, RECIPIENTS_PARAMETER)
    }
}