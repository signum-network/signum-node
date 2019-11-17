package brs.entity

import brs.db.TransactionDb
import brs.objects.Constants
import brs.objects.FluxValues
import brs.peer.Peer
import brs.util.BurstException
import brs.util.convert.*
import brs.util.crypto.Crypto
import brs.util.crypto.signUsing
import brs.util.delegates.Atomic
import brs.util.delegates.AtomicLazy
import brs.util.json.*
import brs.util.logging.safeDebug
import brs.util.logging.safeError
import brs.util.sync.Mutex
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class Block internal constructor(
    private val dp: DependencyProvider,
    val version: Int,
    val timestamp: Int,
    val previousBlockId: Long,
    val totalAmountPlanck: Long,
    val totalFeePlanck: Long,
    val payloadLength: Int,
    val payloadHash: ByteArray,
    val generatorPublicKey: ByteArray,
    val generationSignature: ByteArray,
    blockSignature: ByteArray?,
    val previousBlockHash: ByteArray?,
    transactions: Collection<Transaction>?,
    val nonce: Long,
    val blockATs: ByteArray?,
    height: Int
) {
    var transactions by AtomicLazy {
        val txs = transactionDb().findBlockTransactions(id)
        txs.forEach { it.setBlock(this) }
        txs
    }

    var blockSignature: ByteArray?

    var cumulativeDifficulty: BigInteger = BigInteger.ZERO

    var baseTarget = Constants.INITIAL_BASE_TARGET
    var nextBlockId by Atomic<Long?>(null)
    var height = -1
    var id by AtomicLazy {
        hash.fullHashToId()
    }
    var stringId by AtomicLazy {
        id.toUnsignedString()
    }
    var generatorId by AtomicLazy {
        Account.getId(generatorPublicKey)
    }
    var hash by AtomicLazy<ByteArray> {
        checkNotNull(blockSignature) { "Block is not signed yet" }
        Crypto.sha256().digest(toBytes())
    }

    // Pre-verification stuff
    val verificationLock = Mutex()
    var verified: Boolean = false
    var pocTime: BigInteger? = null

    var peer: Peer? = null
    var byteLength = 0

    fun toJsonObject(): JsonObject {
        val json = JsonObject()
        json.addProperty("version", version)
        json.addProperty("timestamp", timestamp)
        json.addProperty("previousBlock", previousBlockId.toUnsignedString())
        json.addProperty("totalAmountNQT", totalAmountPlanck)
        json.addProperty("totalFeeNQT", totalFeePlanck)
        json.addProperty("payloadLength", payloadLength)
        json.addProperty("payloadHash", payloadHash.toHexString())
        json.addProperty("generatorPublicKey", generatorPublicKey.toHexString())
        json.addProperty("generationSignature", generationSignature.toHexString())
        if (version > 1) {
            json.addProperty("previousBlockHash", previousBlockHash!!.toHexString())
        }
        json.addProperty("blockSignature", blockSignature?.toHexString())
        val transactionsData = JsonArray()
        transactions.forEach { transaction -> transactionsData.add(transaction.toJsonObject()) }
        json.add("transactions", transactionsData)
        json.addProperty("nonce", nonce.toUnsignedString())
        json.addProperty("blockATs", blockATs?.toHexString() ?: "")
        return json
    }

    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(
            4 + 4 + 8 + 4 + (if (version < 3) 4 + 4 else 8 + 8) + 4 + 32 + 32 + (32 + 32) + 8 + (blockATs?.size
                ?: 0) + 64
        )
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(version)
        buffer.putInt(timestamp)
        buffer.putLong(previousBlockId)
        buffer.putInt(transactions.size)
        if (version < 3) {
            buffer.putInt((totalAmountPlanck / Constants.ONE_BURST).toInt())
            buffer.putInt((totalFeePlanck / Constants.ONE_BURST).toInt())
        } else {
            buffer.putLong(totalAmountPlanck)
            buffer.putLong(totalFeePlanck)
        }
        buffer.putInt(payloadLength)
        buffer.put(payloadHash)
        buffer.put(generatorPublicKey)
        buffer.put(generationSignature)
        if (version > 1) {
            buffer.put(previousBlockHash)
        }
        buffer.putLong(nonce)
        if (blockATs != null)
            buffer.put(blockATs)
        if (buffer.limit() - buffer.position() < blockSignature!!.size)
            logger.safeError { "Something is too large here - buffer should have ${blockSignature!!.size} bytes left but only has ${buffer.limit() - buffer.position()}" }
        buffer.put(blockSignature!!)
        return buffer.array()
    }

    init {
        if (payloadLength > dp.fluxCapacitorService.getValue(
                FluxValues.MAX_PAYLOAD_LENGTH,
                height
            ) || payloadLength < 0
        ) {
            throw BurstException.NotValidException(
                "attempted to create a block with payloadLength " + payloadLength + " height " + height + "previd " + previousBlockId
            )
        }
        this.blockSignature = blockSignature
        if (transactions != null) {
            if (transactions.size > dp.fluxCapacitorService.getValue(FluxValues.MAX_NUMBER_TRANSACTIONS, height)) {
                throw BurstException.NotValidException(
                    "attempted to create a block with " + transactions.size + " transactions"
                )
            }
            var previousId: Long = 0
            transactions.forEach { transaction ->
                if (transaction.id <= previousId && previousId != 0L) {
                    throw BurstException.NotValidException("Block transactions are not sorted!")
                }
                previousId = transaction.id
            }
            this.transactions = transactions
        }
    }

    constructor(
        dp: DependencyProvider,
        version: Int,
        timestamp: Int,
        previousBlockId: Long,
        totalAmountPlanck: Long,
        totalFeePlanck: Long,
        payloadLength: Int,
        payloadHash: ByteArray,
        generatorPublicKey: ByteArray,
        generationSignature: ByteArray,
        blockSignature: ByteArray,
        previousBlockHash: ByteArray?,
        cumulativeDifficulty: BigInteger?,
        baseTarget: Long,
        nextBlockId: Long,
        height: Int,
        id: Long,
        nonce: Long,
        blockATs: ByteArray?
    ) : this(
        dp,
        version,
        timestamp,
        previousBlockId,
        totalAmountPlanck,
        totalFeePlanck,
        payloadLength,
        payloadHash,
        generatorPublicKey,
        generationSignature,
        blockSignature,
        previousBlockHash,
        null,
        nonce,
        blockATs,
        height
    ) {

        this.cumulativeDifficulty = cumulativeDifficulty ?: BigInteger.ZERO
        this.baseTarget = baseTarget
        this.nextBlockId = nextBlockId
        this.height = height
        this.id = id
    }

    private fun transactionDb(): TransactionDb {
        return dp.transactionDb
    }

    fun getNonce(): Long? {
        return nonce
    }

    override fun equals(other: Any?): Boolean {
        return other is Block && this.id == other.id
    }

    override fun hashCode(): Int {
        return (id xor id.ushr(32)).toInt()
    }

    internal fun sign(secretPhrase: String) {
        check(blockSignature == null) { "Block already signed" }
        blockSignature = ByteArray(64)
        val data = toBytes()
        val data2 = ByteArray(data.size - 64)
        System.arraycopy(data, 0, data2, 0, data2.size)
        blockSignature = data2.signUsing(secretPhrase)
    }

    companion object {

        private val logger = LoggerFactory.getLogger(Block::class.java)

        internal fun parseBlock(dp: DependencyProvider, blockData: JsonObject, height: Int): Block {
            try {
                val version = blockData.get("version").mustGetAsInt("version")
                val timestamp = blockData.get("timestamp").mustGetAsInt("timestamp")
                val previousBlock = blockData.get("previousBlock").safeGetAsString().parseUnsignedLong()
                val totalAmountPlanck = blockData.get("totalAmountNQT").mustGetAsLong("totalAmountNQT")
                val totalFeePlanck = blockData.get("totalFeeNQT").mustGetAsLong("totalFeeNQT")
                val payloadLength = blockData.get("payloadLength").mustGetAsInt("payloadLength")
                val payloadHash = blockData.get("payloadHash").mustGetAsString("payloadHash").parseHexString()
                val generatorPublicKey =
                    blockData.get("generatorPublicKey").mustGetAsString("generatorPublicKey").parseHexString()
                val generationSignature =
                    blockData.get("generationSignature").mustGetAsString("generationSignature").parseHexString()
                val blockSignature = blockData.get("blockSignature").mustGetAsString("blockSignature").parseHexString()
                val previousBlockHash =
                    if (version == 1) null else blockData.get("previousBlockHash").mustGetAsString("previousBlockHash").parseHexString()
                val nonce = blockData.get("nonce").safeGetAsString().parseUnsignedLong()

                val blockTransactions = TreeMap<Long, Transaction>()
                val transactionsData = blockData.get("transactions").mustGetAsJsonArray("transactions")

                for (transactionData in transactionsData) {
                    val transaction =
                        Transaction.parseTransaction(dp, transactionData.mustGetAsJsonObject("transactionData"), height)
                    if (transaction.signature != null && blockTransactions.put(transaction.id, transaction) != null) {
                        throw BurstException.NotValidException("Block contains duplicate transactions: " + transaction.stringId)
                    }
                }

                val blockATs = blockData.get("blockATs").safeGetAsString()?.parseHexString()
                return Block(
                    dp, version, timestamp, previousBlock, totalAmountPlanck, totalFeePlanck,
                    payloadLength, payloadHash, generatorPublicKey, generationSignature, blockSignature,
                    previousBlockHash, blockTransactions.values, nonce, blockATs, height
                )
            } catch (e: BurstException.ValidationException) {
                logger.safeDebug { "Failed to parse block: ${blockData.toJsonString()}" }
                throw e
            } catch (e: RuntimeException) {
                logger.safeDebug { "Failed to parse block: ${blockData.toJsonString()}" }
                throw e
            }

        }
    }
}
