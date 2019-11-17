package brs.services.impl

import brs.api.http.common.ResultFields.UNCONFIRMED_TRANSACTIONS_RESPONSE
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.objects.FluxValues
import brs.objects.Props
import brs.peer.Peer
import brs.services.TaskType
import brs.services.TransactionProcessorService
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.BurstException.ValidationException
import brs.util.Listeners
import brs.util.json.*
import brs.util.logging.safeDebug
import brs.util.logging.safeError
import brs.util.logging.safeInfo
import brs.util.logging.safeTrace
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory

class TransactionProcessorServiceImpl(private val dp: DependencyProvider) : TransactionProcessorService {
    private val testUnconfirmedTransactions = dp.propertyService.get(Props.BRS_TEST_UNCONFIRMED_TRANSACTIONS)

    private val transactionListeners = Listeners<Collection<Transaction>, TransactionProcessorService.Event>()
    private val foodDispenser: (Peer) -> Collection<Transaction> = { dp.unconfirmedTransactionService.getAllFor(it) }
    private val doneFeedingLog: (Peer, Collection<Transaction>) -> Unit =
        { peer, transactions -> dp.unconfirmedTransactionService.markFingerPrintsOf(peer, transactions) }

    init {
        dp.taskSchedulerService.scheduleTaskWithDelay(TaskType.IO, 0, 10000) {
            run {
                try {
                    val peer = dp.peerService.getAnyPeer(Peer.State.CONNECTED) ?: return@run
                    val response = dp.peerService.readUnconfirmedTransactions(peer) ?: return@run
                    val transactionsData = response.get(UNCONFIRMED_TRANSACTIONS_RESPONSE).safeGetAsJsonArray()
                    if (transactionsData == null || transactionsData.isEmpty()) return@run
                    dp.peerService.feedingTime(peer, foodDispenser, doneFeedingLog)

                    try {
                        val addedTransactions = processPeerTransactions(transactionsData, peer)

                        if (addedTransactions.isNotEmpty()) {
                            val activePriorityPlusExtra = dp.peerService.allActivePriorityPlusSomeExtraPeers
                            activePriorityPlusExtra.remove(peer)

                            dp.taskSchedulerService.awaitTasks(TaskType.IO, activePriorityPlusExtra.map { otherPeer ->
                                {
                                    try {
                                        val otherPeerResponse = dp.peerService.readUnconfirmedTransactions(otherPeer)
                                        if (otherPeerResponse != null) {
                                            val otherPeerTransactions =
                                                otherPeerResponse.get(UNCONFIRMED_TRANSACTIONS_RESPONSE)
                                                    .mustGetAsJsonArray(UNCONFIRMED_TRANSACTIONS_RESPONSE)
                                            if (!otherPeerTransactions.isEmpty()) dp.peerService.feedingTime(
                                                otherPeer,
                                                foodDispenser,
                                                doneFeedingLog
                                            )
                                        }
                                    } catch (e: ValidationException) {
                                        peer.blacklist(e, "pulled invalid data using getUnconfirmedTransactions")
                                    } catch (e: RuntimeException) {
                                        peer.blacklist(e, "pulled invalid data using getUnconfirmedTransactions")
                                    }
                                }
                            })
                        }
                    } catch (e: ValidationException) {
                        peer.blacklist(e, "pulled invalid data using getUnconfirmedTransactions")
                    } catch (e: RuntimeException) {
                        peer.blacklist(e, "pulled invalid data using getUnconfirmedTransactions")
                    }
                } catch (e: Exception) {
                    logger.safeDebug(e) { "Error processing unconfirmed transactions" }
                }
            }
        }
    }

    override val allUnconfirmedTransactions: List<Transaction>
        get() = dp.unconfirmedTransactionService.all

    override val amountUnconfirmedTransactions: Int
        get() = dp.unconfirmedTransactionService.amount

    override fun addListener(
        eventType: TransactionProcessorService.Event,
        listener: (Collection<Transaction>) -> Unit
    ) {
        return transactionListeners.addListener(eventType, listener)
    }

    override fun notifyListeners(transactions: Collection<Transaction>, eventType: TransactionProcessorService.Event) {
        transactionListeners.accept(eventType, transactions)
    }

    override fun getAllUnconfirmedTransactionsFor(peer: Peer): Collection<Transaction> {
        return dp.unconfirmedTransactionService.getAllFor(peer)
    }

    override fun markFingerPrintsOf(peer: Peer, transactions: Collection<Transaction>) {
        dp.unconfirmedTransactionService.markFingerPrintsOf(peer, transactions)
    }

    override fun getUnconfirmedTransaction(transactionId: Long): Transaction? {
        return dp.unconfirmedTransactionService.get(transactionId)
    }

    override fun newTransactionBuilder(
        senderPublicKey: ByteArray,
        amountPlanck: Long,
        feePlanck: Long,
        deadline: Short,
        attachment: Attachment
    ): Transaction.Builder {
        val version = getTransactionVersion(dp.blockchainService.height).toByte()
        val timestamp = dp.timeService.epochTime
        val builder = Transaction.Builder(
            dp,
            version,
            senderPublicKey,
            amountPlanck,
            feePlanck,
            timestamp,
            deadline,
            attachment as Attachment.AbstractAttachment
        )
        if (version > 0) {
            val ecBlock = dp.economicClusteringService.getECBlock(timestamp)
            builder.ecBlockHeight(ecBlock.height)
            builder.ecBlockId(ecBlock.id)
        }
        return builder
    }

    override fun broadcast(transaction: Transaction): Int? {
        if (!transaction.verifySignature()) {
            throw BurstException.NotValidException("Transaction signature verification failed")
        }
        val processedTransactions = processTransactions(setOf(transaction), null)
        if (dp.transactionDb.hasTransaction(transaction.id)) {
            logger.safeInfo { "Transaction ${transaction.stringId} already in blockchain, will not broadcast again" }
            return null
        }

        if (dp.unconfirmedTransactionService.exists(transaction.id)) {
            logger.safeInfo { "Transaction ${transaction.stringId} already in unconfirmed pool, will not broadcast again" }
            return null
        }

        if (processedTransactions.isNotEmpty()) {
            return broadcastToPeers(true)
        } else {
            logger.safeDebug { "Could not accept new transaction ${transaction.stringId}" }
            throw BurstException.NotValidException("Invalid transaction " + transaction.stringId)
        }
    }

    override fun processPeerTransactions(request: JsonObject, peer: Peer) {
        val transactionsData = request.get("transactions").mustGetAsJsonArray("transactions")
        val processedTransactions = processPeerTransactions(transactionsData, peer)

        if (!processedTransactions.isEmpty()) {
            broadcastToPeers(false)
        }
    }

    override fun parseTransaction(bytes: ByteArray): Transaction {
        return Transaction.parseTransaction(dp, bytes)
    }

    override fun parseTransaction(transactionData: JsonObject): Transaction {
        return Transaction.parseTransaction(dp, transactionData, dp.blockchainService.height)
    }

    override fun clearUnconfirmedTransactions() {
        val removed: List<Transaction>
        try {
            dp.db.beginTransaction()
            removed = dp.unconfirmedTransactionService.all
            dp.accountService.flushAccountTable()
            dp.unconfirmedTransactionService.clear()
            dp.db.commitTransaction()
        } catch (e: Exception) {
            logger.safeError(e) { e.toString() }
            dp.db.rollbackTransaction()
            throw e
        } finally {
            dp.db.endTransaction()
        }

        transactionListeners.accept(TransactionProcessorService.Event.REMOVED_UNCONFIRMED_TRANSACTIONS, removed)
    }

    override fun requeueAllUnconfirmedTransactions() {
        dp.unconfirmedTransactionService.resetAccountBalances()
    }

    override fun getTransactionVersion(blockHeight: Int): Int {
        return if (dp.fluxCapacitorService.getValue(FluxValues.DIGITAL_GOODS_STORE, blockHeight)) 1 else 0
    }

    // Watch: This is not really clean
    override fun processLater(transactions: Collection<Transaction>) {
        for (transaction in transactions) {
            try {
                dp.unconfirmedTransactionService.put(transaction, null)
            } catch (e: ValidationException) {
                logger.safeDebug(e) { "Discarding invalid transaction in for later processing: " + transaction.toJsonObject().toJsonString() }
            }
        }
    }

    private fun processPeerTransactions(transactionsData: JsonArray, peer: Peer): Collection<Transaction> {
        if (dp.blockchainService.lastBlock.timestamp < dp.timeService.epochTime - 60 * 1440 && !testUnconfirmedTransactions) {
            return mutableListOf()
        }
        val transactions = mutableListOf<Transaction>()
        for (transactionData in transactionsData) {
            try {
                val transaction = parseTransaction(transactionData.mustGetAsJsonObject("transaction"))
                dp.transactionService.validate(transaction)
                if (!dp.economicClusteringService.verifyFork(transaction)) {
                    continue
                }
                transactions.add(transaction)
            } catch (ignored: BurstException.NotCurrentlyValidException) {
            } catch (e: BurstException.NotValidException) {
                logger.safeDebug { "Invalid transaction from peer: ${transactionData.toJsonString()}" }
                throw e
            }

        }
        return processTransactions(transactions, peer)
    }

    private fun processTransactions(transactions: Collection<Transaction>, peer: Peer?): Collection<Transaction> {
        if (transactions.isEmpty()) {
            return emptyList()
        }

        val addedUnconfirmedTransactions = mutableListOf<Transaction>()

        for (transaction in transactions) {

            try {
                val curTime = dp.timeService.epochTime
                if (transaction.timestamp > curTime + 15 || transaction.expiration < curTime
                    || transaction.deadline > 1440
                ) {
                    continue
                }

                dp.db.beginTransaction()
                try {
                    if (dp.transactionDb.hasTransaction(transaction.id) || dp.unconfirmedTransactionService.exists(
                            transaction.id
                        )
                    ) {
                        dp.unconfirmedTransactionService.markFingerPrintsOf(peer, listOf(transaction))
                    } else if (!(transaction.verifySignature() && dp.transactionService.verifyPublicKey(transaction))) {
                        if (dp.accountService.getAccount(transaction.senderId) != null) {
                            logger.safeDebug { "Transaction ${transaction.toJsonObject().toJsonString()} failed to verify" }
                        }
                    } else if (dp.unconfirmedTransactionService.put(transaction, peer)) {
                        addedUnconfirmedTransactions.add(transaction)
                    }
                    dp.db.commitTransaction()
                } catch (e: Exception) {
                    dp.db.rollbackTransaction()
                    throw e
                } finally {
                    dp.db.endTransaction()
                }
            } catch (e: RuntimeException) {
                logger.safeInfo(e) { "Error processing transaction" }
            }

        }

        if (addedUnconfirmedTransactions.isNotEmpty()) {
            transactionListeners.accept(
                TransactionProcessorService.Event.ADDED_UNCONFIRMED_TRANSACTIONS,
                addedUnconfirmedTransactions
            )
        }

        return addedUnconfirmedTransactions
    }

    private fun broadcastToPeers(toAll: Boolean): Int {
        val peersToSendTo =
            if (toAll) dp.peerService.activePeers.take(100) else dp.peerService.allActivePriorityPlusSomeExtraPeers

        logger.safeTrace { "Queueing up ${peersToSendTo.size} Peers for feeding" }

        for (p in peersToSendTo) {
            dp.peerService.feedingTime(p, foodDispenser, doneFeedingLog)
        }

        return peersToSendTo.size
    }

    override fun revalidateUnconfirmedTransactions() {
        val invalidTransactions = mutableListOf<Transaction>()

        for (t in dp.unconfirmedTransactionService.all) {
            try {
                dp.transactionService.validate(t)
            } catch (e: ValidationException) {
                invalidTransactions.add(t)
            }
        }

        for (t in invalidTransactions) {
            dp.unconfirmedTransactionService.remove(t)
        }
    }

    override fun removeForgedTransactions(transactions: Collection<Transaction>) {
        dp.unconfirmedTransactionService.removeForgedTransactions(transactions)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TransactionProcessorServiceImpl::class.java)
    }
}
