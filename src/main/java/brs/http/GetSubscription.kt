package brs.http

import brs.Subscription
import brs.services.SubscriptionService
import brs.util.Convert
import com.google.gson.JsonElement
import com.google.gson.JsonObject

import javax.servlet.http.HttpServletRequest

import brs.http.common.Parameters.SUBSCRIPTION_PARAMETER
import brs.http.common.ResultFields.ERROR_CODE_RESPONSE
import brs.http.common.ResultFields.ERROR_DESCRIPTION_RESPONSE

internal class GetSubscription(private val subscriptionService: SubscriptionService) : APIServlet.JsonRequestHandler(arrayOf(APITag.ACCOUNTS), SUBSCRIPTION_PARAMETER) {

    internal override fun processRequest(req: HttpServletRequest): JsonElement {
        val subscriptionId: Long
        try {
            subscriptionId = Convert.parseUnsignedLong(Convert.emptyToNull(req.getParameter(SUBSCRIPTION_PARAMETER)))
        } catch (e: Exception) {
            val response = JsonObject()
            response.addProperty(ERROR_CODE_RESPONSE, 3)
            response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Invalid or not specified subscription")
            return response
        }

        val subscription = subscriptionService.getSubscription(subscriptionId)

        if (subscription == null) {
            val response = JsonObject()
            response.addProperty(ERROR_CODE_RESPONSE, 5)
            response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Subscription not found")
            return response
        }

        return JSONData.subscription(subscription)
    }
}