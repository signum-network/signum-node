package brs.peer

import brs.BurstException
import brs.TransactionProcessor
import brs.util.JSON
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal class ProcessTransactions(private val transactionProcessor: TransactionProcessor) : PeerServlet.PeerRequestHandler {
    override fun processRequest(request: JsonObject, peer: Peer): JsonElement {
        return try {
            transactionProcessor.processPeerTransactions(request, peer) // TODO this is not locking sync obj...
            JSON.emptyJSON
        } catch (e: RuntimeException) {
            peer.blacklist(e, "received invalid data via requestType=processTransactions")
            val response = JsonObject()
            response.addProperty("error", e.toString())
            response
        } catch (e: BurstException.ValidationException) {
            peer.blacklist(e, "received invalid data via requestType=processTransactions")
            val response = JsonObject()
            response.addProperty("error", e.toString())
            response
        }
    }
}