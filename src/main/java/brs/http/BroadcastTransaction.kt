package brs.http

import brs.BurstException
import brs.Transaction
import brs.TransactionProcessor
import brs.services.ParameterService
import brs.services.TransactionService
import brs.util.Convert
import com.google.gson.JsonElement
import com.google.gson.JsonObject

import javax.servlet.http.HttpServletRequest
import java.util.logging.Level
import java.util.logging.Logger

import brs.http.common.Parameters.TRANSACTION_BYTES_PARAMETER
import brs.http.common.Parameters.TRANSACTION_JSON_PARAMETER
import brs.http.common.ResultFields.*

internal class BroadcastTransaction(private val transactionProcessor: TransactionProcessor, private val parameterService: ParameterService, private val transactionService: TransactionService) : APIServlet.JsonRequestHandler(arrayOf(APITag.TRANSACTIONS), TRANSACTION_BYTES_PARAMETER, TRANSACTION_JSON_PARAMETER) {

    @Throws(BurstException::class)
    internal override fun processRequest(req: HttpServletRequest): JsonElement {

        val transactionBytes = Convert.emptyToNull(req.getParameter(TRANSACTION_BYTES_PARAMETER))
        val transactionJSON = Convert.emptyToNull(req.getParameter(TRANSACTION_JSON_PARAMETER))
        val transaction = parameterService.parseTransaction(transactionBytes, transactionJSON)
        val response = JsonObject()
        try {
            transactionService.validate(transaction)
            response.addProperty(NUMBER_PEERS_SENT_TO_RESPONSE, transactionProcessor.broadcast(transaction))
            response.addProperty(TRANSACTION_RESPONSE, transaction.stringId)
            response.addProperty(FULL_HASH_RESPONSE, transaction.fullHash)
        } catch (e: BurstException.ValidationException) {
            logger.log(Level.INFO, e.message, e)
            response.addProperty(ERROR_CODE_RESPONSE, 4)
            response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Incorrect transaction: $e")
            response.addProperty(ERROR_RESPONSE, e.message)
        } catch (e: RuntimeException) {
            logger.log(Level.INFO, e.message, e)
            response.addProperty(ERROR_CODE_RESPONSE, 4)
            response.addProperty(ERROR_DESCRIPTION_RESPONSE, "Incorrect transaction: $e")
            response.addProperty(ERROR_RESPONSE, e.message)
        }

        return response

    }

    internal override fun requirePost(): Boolean {
        return true
    }

    companion object {

        private val logger = Logger.getLogger(BroadcastTransaction::class.java.simpleName)
    }

}