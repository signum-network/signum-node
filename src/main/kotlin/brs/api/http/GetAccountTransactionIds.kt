package brs.api.http

import brs.api.http.common.Parameters.ACCOUNT_PARAMETER
import brs.api.http.common.Parameters.FIRST_INDEX_PARAMETER
import brs.api.http.common.Parameters.INCLUDE_INDIRECT_PARAMETER
import brs.api.http.common.Parameters.LAST_INDEX_PARAMETER
import brs.api.http.common.Parameters.NUMBER_OF_CONFIRMATIONS_PARAMETER
import brs.api.http.common.Parameters.SUBTYPE_PARAMETER
import brs.api.http.common.Parameters.TIMESTAMP_PARAMETER
import brs.api.http.common.Parameters.TYPE_PARAMETER
import brs.services.BlockchainService
import brs.services.ParameterService
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import javax.servlet.http.HttpServletRequest

/**
 * TODO
 */
internal class GetAccountTransactionIds(
    private val parameterService: ParameterService,
    private val blockchainService: BlockchainService
) : APIServlet.JsonRequestHandler(
    arrayOf(APITag.ACCOUNTS),
    ACCOUNT_PARAMETER,
    TIMESTAMP_PARAMETER,
    TYPE_PARAMETER,
    SUBTYPE_PARAMETER,
    ACCOUNT_PARAMETER,
    TIMESTAMP_PARAMETER,
    FIRST_INDEX_PARAMETER,
    LAST_INDEX_PARAMETER,
    NUMBER_OF_CONFIRMATIONS_PARAMETER,
    INCLUDE_INDIRECT_PARAMETER
) {
    override fun processRequest(request: HttpServletRequest): JsonElement {
        val account = parameterService.getAccount(request) ?: return JSONResponses.INCORRECT_ACCOUNT
        val timestamp = ParameterParser.getTimestamp(request)
        val numberOfConfirmations = parameterService.getNumberOfConfirmations(request)

        val type: Byte = try {
            request.getParameter(TYPE_PARAMETER)?.toByte() ?: -1
        } catch (e: NumberFormatException) {
            -1
        }
        val subtype: Byte = try {
            request.getParameter(SUBTYPE_PARAMETER)?.toByte() ?: -1
        } catch (e: NumberFormatException) {
            -1
        }

        val firstIndex = ParameterParser.getFirstIndex(request)
        val lastIndex = ParameterParser.getLastIndex(request)

        val transactionIds = JsonArray()
        for (transaction in blockchainService.getTransactions(
            account,
            numberOfConfirmations,
            type,
            subtype,
            timestamp,
            firstIndex,
            lastIndex,
            parameterService.getIncludeIndirect(request)
        )) {
            transactionIds.add(transaction.stringId)
        }

        val response = JsonObject()
        response.add("transactionIds", transactionIds)
        return response

    }
}
