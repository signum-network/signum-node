package brs.services.impl

import brs.db.transaction
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.objects.Constants.MAX_TIMESTAMP_DIFFERENCE
import brs.objects.FluxValues
import brs.objects.Props
import brs.peer.Peer
import brs.services.TaskType
import brs.services.TransactionProcessorService
import brs.transaction.appendix.Attachment
import brs.util.BurstException
import brs.util.BurstException.ValidationException
import brs.util.Listeners
import brs.util.json.toJsonString
import brs.util.logging.safeDebug
import brs.util.logging.safeInfo
import brs.util.logging.safeTrace
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
                    val transactions = peer.getUnconfirmedTransactions()
                    if (transactions.isNullOrEmpty()) return@run
                    dp.peerService.feedingTime(peer, foodDispenser, doneFeedingLog)

                    try {
                        val addedTransactions = processPeerTransactions(transactions, peer, false)
                        if (addedTransactions.isNotEmpty()) {
                            val activePriorityPlusExtra = dp.peerService.allActivePriorityPlusSomeExtraPeers
                            activePriorityPlusExtra.remove(peer)

                            dp.taskSchedulerService.awaitTasks(TaskType.IO, activePriorityPlusExtra.map { otherPeer -> {
                                    try {
                                        val otherPeerTransactions = otherPeer.getUnconfirmedTransactions()
                                        if (!otherPeerTransactions.isNullOrEmpty())
                                            dp.peerService.feedingTime(otherPeer, foodDispenser, doneFeedingLog)
                                    } catch (e: Exception) {
                                        peer.blacklist(e, "pulled invalid data using getUnconfirmedTransactions")
                                    }
                                }
                            })
                        }
                    } catch (e: ValidationException) {
                        peer.blacklist(e, "pulled invalid data using getUnconfirmedTransactions")
                    } catch (e: Exception) {
                        peer.blacklist(e, "pulled invalid data using getUnconfirmedTransactions")
                    }
                } catch (e: Exception) {
                    logger.safeDebug(e) { "Error processing unconfirmed transactions" }
                }
            }
        }
    }

    override fun addListener(
        eventType: TransactionProcessorService.Event,
        listener: (Collection<Transaction>) -> Unit
    ) {
        return transactionListeners.addListener(eventType, listener)
    }

    override fun notifyListeners(transactions: Collection<Transaction>, eventType: TransactionProcessorService.Event) {
        transactionListeners.accept(eventType, transactions)
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

        if (dp.unconfirmedTransactionService.exists(transaction.id)) {
            logger.safeDebug { "Transaction ${transaction.stringId} already in unconfirmed pool, will not broadcast again" }
            return null
        }

        val processedTransactions = processTransactions(listOf(transaction), null)
        if (dp.transactionDb.hasTransaction(transaction.id)) {
            logger.safeDebug { "Transaction ${transaction.stringId} already in blockchain, will not broadcast again" }
            return null
        }

        if (processedTransactions.isNotEmpty()) {
            return broadcastToPeers(true)
        } else {
            logger.safeDebug { "Could not accept new transaction ${transaction.stringId}" }
            throw BurstException.NotValidException("Invalid transaction " + transaction.stringId)
        }
    }

    fun parseTransaction(transactionData: JsonObject): Transaction {
        return Transaction.parseTransaction(dp, transactionData)
    }

    override fun clearUnconfirmedTransactions() {
        val removed: List<Transaction>
        dp.db.transaction {
            removed = dp.unconfirmedTransactionService.all
            dp.accountService.flushAccountTable()
            dp.unconfirmedTransactionService.clear()
        }
        transactionListeners.accept(TransactionProcessorService.Event.REMOVED_UNCONFIRMED_TRANSACTIONS, removed)
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

    override fun processPeerTransactions(transactions: Collection<Transaction>, peer: Peer, rebroadcast: Boolean): Collection<Transaction> {
        if (dp.blockchainService.lastBlock.timestamp < dp.timeService.epochTime - 60 * 1440 && !testUnconfirmedTransactions) return emptyList()

        val filteredTransactions = transactions.filter {
            try {
                dp.transactionService.validate(it)
                dp.economicClusteringService.verifyFork(it)
            } catch (e: Exception) {
                false
            }
        }

        val processed = processTransactions(filteredTransactions, peer)
        if (rebroadcast && processed.isNotEmpty()) {
            broadcastToPeers(false)
        }
        return processed
    }

    private fun processTransactions(transactions: Collection<Transaction>, peer: Peer?): Collection<Transaction> {
        if (transactions.isEmpty()) {
            return emptyList()
        }

        val addedUnconfirmedTransactions = mutableListOf<Transaction>()

        for (transaction in transactions) {
            try {
                val curTime = dp.timeService.epochTime
                if (transaction.timestamp > curTime + MAX_TIMESTAMP_DIFFERENCE || transaction.expiration < curTime || transaction.deadline > 1440) continue

                dp.db.transaction {
                    when {
                        dp.transactionDb.hasTransaction(transaction.id) || dp.unconfirmedTransactionService.exists(transaction.id) ->
                            dp.unconfirmedTransactionService.markFingerPrintsOf(peer, transaction)
                        !transaction.verifySignature() || !dp.transactionService.verifyPublicKey(transaction) ->
                            logger.safeDebug { "Transaction ${transaction.toJsonObject().toJsonString()} failed to verify" }
                        dp.unconfirmedTransactionService.put(transaction, peer) -> addedUnconfirmedTransactions.add(transaction)
                    }
                }
            } catch (e: Exception) {
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

    companion object {
        private val logger = LoggerFactory.getLogger(TransactionProcessorServiceImpl::class.java)
    }
}
