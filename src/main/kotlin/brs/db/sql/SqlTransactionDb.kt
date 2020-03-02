package brs.db.sql

import brs.db.TransactionDb
import brs.db.useDslContext
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.schema.Tables.TRANSACTION
import brs.schema.tables.records.TransactionRecord
import brs.transaction.appendix.Appendix
import brs.transaction.type.TransactionType
import brs.util.BurstException
import brs.util.cache.tryCache
import brs.util.convert.toUnsignedString
import brs.util.db.fetchAndMap
import org.ehcache.Cache
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class SqlTransactionDb(private val dp: DependencyProvider) : TransactionDb {
    private val cache: Cache<Long, Transaction>
        get() = dp.dbCacheService.getCache("transaction", Long::class.javaObjectType, Transaction::class.java)!!

    override fun findTransaction(transactionId: Long): Transaction? {
        return cache.tryCache(transactionId) {
            dp.db.useDslContext { ctx ->
                try {
                    val transactionRecord = ctx.selectFrom(TRANSACTION).where(TRANSACTION.ID.eq(transactionId)).fetchOne() ?: return null
                    return@useDslContext loadTransaction(transactionRecord)
                } catch (e: BurstException.ValidationException) {
                    throw Exception("Transaction already in database, id = $transactionId, does not pass validation!", e)
                }
            }
        }
    }

    override fun findTransactionByFullHash(fullHash: ByteArray): Transaction? {
        // TODO this isn't cached
        return dp.db.useDslContext { ctx ->
            try {
                val transactionRecord = ctx.selectFrom(TRANSACTION).where(TRANSACTION.FULL_HASH.eq(fullHash)).fetchOne() ?: return null
                return@useDslContext loadTransaction(transactionRecord)
            } catch (e: BurstException.ValidationException) {
                throw Exception("Transaction already in database, full_hash = $fullHash, does not pass validation!", e)
            }
        }
    }

    override fun hasTransaction(transactionId: Long): Boolean {
        // TODO this isn't cached
        return dp.db.useDslContext { ctx ->
            ctx.fetchExists(
                ctx.selectFrom(TRANSACTION).where(
                    TRANSACTION.ID.eq(
                        transactionId
                    )
                )
            )
        }
    }

    override fun hasTransactionByFullHash(fullHash: ByteArray): Boolean {
        // TODO this isn't cached
        return dp.db.useDslContext { ctx ->
            ctx.fetchExists(ctx.selectFrom(TRANSACTION).where(TRANSACTION.FULL_HASH.eq(fullHash)))
        }
    }

    override fun loadTransaction(record: TransactionRecord): Transaction {
        val buffer: ByteBuffer
        if (record.attachmentBytes != null) {
            buffer = ByteBuffer.wrap(record.attachmentBytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
        } else {
            buffer = ByteBuffer.allocate(0)
        }

        val transactionType = TransactionType.findTransactionType(dp, record.type, record.subtype) ?: error("Could not find transaction with type ${record.type} and subtype ${record.subtype}")
        val builder = Transaction.Builder(
            dp, record.version, record.senderPublicKey,
            record.amount, record.fee, record.timestamp, record.deadline,
            transactionType.parseAttachment(buffer, record.version)
        )
            .signature(record.signature)
            .blockId(record.blockId)
            .height(record.height)
            .id(record.id)
            .senderId(record.senderId)
            .blockTimestamp(record.blockTimestamp)
            .fullHash(record.fullHash)
        val referencedTransactionFullHash = record.referencedTransactionFullhash
        if (referencedTransactionFullHash != null) builder.referencedTransactionFullHash(referencedTransactionFullHash)
        if (transactionType.hasRecipient() && record.recipientId != null) builder.recipientId(record.recipientId)
        if (record.hasMessage) builder.message(Appendix.Message(buffer, record.version))
        if (record.hasEncryptedMessage) builder.encryptedMessage(Appendix.EncryptedMessage(buffer, record.version))
        if (record.hasPublicKeyAnnouncement) builder.publicKeyAnnouncement(Appendix.PublicKeyAnnouncement(dp, buffer, record.version))
        if (record.hasEncrypttoselfMessage) builder.encryptToSelfMessage(Appendix.EncryptToSelfMessage(buffer, record.version))
        if (record.version > 0) {
            builder.ecBlockHeight(record.ecBlockHeight)
            if (record.ecBlockId != null) builder.ecBlockId(record.ecBlockId)
        }

        return builder.build()
    }

    override fun findBlockTransactions(blockId: Long): Collection<Transaction> {
        // TODO this isn't cached
        return dp.db.useDslContext { ctx ->
            ctx.selectFrom(TRANSACTION)
                .where(TRANSACTION.BLOCK_ID.eq(blockId))
                .and(TRANSACTION.SIGNATURE.isNotNull)
                .fetchAndMap { record ->
                    try {
                        return@fetchAndMap loadTransaction(record)
                    } catch (e: BurstException.ValidationException) {
                        throw Exception(
                            "Transaction already in database for block_id = ${blockId.toUnsignedString()} does not pass validation!",
                            e
                        )
                    }
                }
        }
    }

    private fun getAttachmentBytes(transaction: Transaction): ByteArray? {
        var bytesLength = 0
        for (appendage in transaction.appendages) {
            bytesLength += appendage.size
        }
        return if (bytesLength == 0) {
            null
        } else {
            val buffer = ByteBuffer.allocate(bytesLength)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            for (appendage in transaction.appendages) {
                appendage.putBytes(buffer)
            }
            buffer.array()
        }
    }

    override fun saveTransactions(transactions: Collection<Transaction>) {
        if (transactions.isNotEmpty()) {
            dp.db.useDslContext { ctx ->
                var insertQuery = ctx.insertInto(
                    TRANSACTION,
                    TRANSACTION.ID,
                    TRANSACTION.DEADLINE,
                    TRANSACTION.SENDER_PUBLIC_KEY,
                    TRANSACTION.RECIPIENT_ID,
                    TRANSACTION.AMOUNT,
                    TRANSACTION.FEE,
                    TRANSACTION.REFERENCED_TRANSACTION_FULLHASH,
                    TRANSACTION.HEIGHT,
                    TRANSACTION.BLOCK_ID,
                    TRANSACTION.SIGNATURE,
                    TRANSACTION.TIMESTAMP,
                    TRANSACTION.TYPE,
                    TRANSACTION.SUBTYPE,
                    TRANSACTION.SENDER_ID,
                    TRANSACTION.ATTACHMENT_BYTES,
                    TRANSACTION.BLOCK_TIMESTAMP,
                    TRANSACTION.FULL_HASH,
                    TRANSACTION.VERSION,
                    TRANSACTION.HAS_MESSAGE,
                    TRANSACTION.HAS_ENCRYPTED_MESSAGE,
                    TRANSACTION.HAS_PUBLIC_KEY_ANNOUNCEMENT,
                    TRANSACTION.HAS_ENCRYPTTOSELF_MESSAGE,
                    TRANSACTION.EC_BLOCK_HEIGHT,
                    TRANSACTION.EC_BLOCK_ID
                )
                transactions.forEach { transaction ->
                    insertQuery = insertQuery.values(
                        transaction.id,
                        transaction.deadline,
                        transaction.senderPublicKey,
                        transaction.recipientId,
                        transaction.amountPlanck,
                        transaction.feePlanck,
                        transaction.referencedTransactionFullHash,
                        transaction.height,
                        transaction.blockId,
                        transaction.signature,
                        transaction.timestamp,
                        transaction.type.type,
                        transaction.type.subtype,
                        transaction.senderId,
                        getAttachmentBytes(transaction),
                        transaction.blockTimestamp,
                        transaction.fullHash,
                        transaction.version,
                        transaction.message != null,
                        transaction.encryptedMessage != null,
                        transaction.publicKeyAnnouncement != null,
                        transaction.encryptToSelfMessage != null,
                        transaction.ecBlockHeight,
                        transaction.ecBlockId
                    )
                }
                insertQuery.execute()
            }
        }
    }

    override fun optimize() {
        dp.db.optimizeTable(TRANSACTION.name)
    }
}
