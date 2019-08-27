package brs.http

import brs.*
import brs.services.ParameterService
import brs.services.SubscriptionService
import brs.util.Convert
import com.google.gson.JsonElement
import com.google.gson.JsonObject

import javax.servlet.http.HttpServletRequest

import brs.http.common.Parameters.SUBSCRIPTION_PARAMETER
import brs.http.common.ResultFields.ERROR_CODE_RESPONSE
import brs.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE

internal class SubscriptionCancel(private val parameterService: ParameterService, private val subscriptionService: SubscriptionService, private val blockchain: Blockchain, apiTransactionManager: APITransactionManager) : CreateTransaction(arrayOf(APITag.TRANSACTIONS, APITag.CREATE_TRANSACTION), apiTransactionManager, SUBSCRIPTION_PARAMETER) {

    @Throws(BurstException::class)
    internal override fun processRequest(req: HttpServletRequest): JsonElement {
        val sender = parameterService.getSenderAccount(req)

        val subscriptionString = Convert.emptyToNull(req.getParameter(SUBSCRIPTION_PARAMETER))
        if (subscriptionString == null) {
            val response = JsonObject()
            response.addProperty(ERROR_CODE_RESPONSE, 3)
            response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Subscription Id not specified")
            return response
        }

        val subscriptionId: Long
        try {
            subscriptionId = Convert.parseUnsignedLong(subscriptionString)
        } catch (e: Exception) {
            val response = JsonObject()
            response.addProperty(ERROR_CODE_RESPONSE, 4)
            response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Failed to parse subscription id")
            return response
        }

        val subscription = subscriptionService.getSubscription(subscriptionId)
        if (subscription == null) {
            val response = JsonObject()
            response.addProperty(ERROR_CODE_RESPONSE, 5)
            response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Subscription not found")
            return response
        }

        if (sender.getId() != subscription.getSenderId() && sender.getId() != subscription.getRecipientId()) {
            val response = JsonObject()
            response.addProperty(ERROR_CODE_RESPONSE, 7)
            response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Must be sender or recipient to cancel subscription")
            return response
        }

        val attachment = Attachment.AdvancedPaymentSubscriptionCancel(subscription.getId(), blockchain.height)

        return createTransaction(req, sender, null, 0, attachment)
    }
}