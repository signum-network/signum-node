package brs.db.sql

import brs.at.AT
import brs.db.*
import brs.entity.DependencyProvider
import brs.schema.Tables.*
import brs.schema.tables.records.AtRecord
import brs.schema.tables.records.AtStateRecord
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SortField
import org.jooq.exception.DataAccessException
import brs.schema.Tables.AT as ATTable

internal class SqlATStore(private val dp: DependencyProvider) : ATStore {
    override val atDbKeyFactory = object : SqlDbKey.LongKeyFactory<AT>(ATTable.ID) {
        override fun newKey(at: AT): BurstKey {
            return at.dbKey
        }
    }

    override val atTable: VersionedEntityTable<AT>

    override val atStateDbKeyFactory = object : SqlDbKey.LongKeyFactory<AT.ATState>(AT_STATE.AT_ID) {
        override fun newKey(atState: AT.ATState): BurstKey {
            return atState.dbKey
        }
    }

    override val atStateTable: VersionedEntityTable<AT.ATState>

    override fun getOrderedATs() = dp.db.getUsingDslContext<List<Long>> { ctx ->
        ctx.selectFrom(ATTable.join(AT_STATE).on(ATTable.ID.eq(AT_STATE.AT_ID)).join(ACCOUNT).on(ATTable.ID.eq(ACCOUNT.ID)))
            .where(ATTable.LATEST.isTrue)
            .and(AT_STATE.LATEST.isTrue)
            .and(ACCOUNT.LATEST.isTrue)
            .and(AT_STATE.NEXT_HEIGHT.lessOrEqual(dp.blockchainService.height + 1))
            .and(
                ACCOUNT.BALANCE.greaterOrEqual(
                    dp.atConstants.stepFee(dp.blockchainService.height) * dp.atConstants.apiStepMultiplier(
                        dp.blockchainService.height
                    )
                )
            )
            .and(AT_STATE.FREEZE_WHEN_SAME_BALANCE.isFalse.or("account.balance - at_state.prev_balance >= at_state.min_activate_amount"))
            .orderBy(AT_STATE.PREV_HEIGHT.asc(), AT_STATE.NEXT_HEIGHT.asc(), ATTable.ID.asc())
            .fetch()
            .getValues(ATTable.ID)
    }

    override fun getAllATIds() = dp.db.getUsingDslContext<Collection<Long>> { ctx ->
        ctx.selectFrom(ATTable).where(ATTable.LATEST.isTrue).fetch().getValues(ATTable.ID)
    }

    init {
        atTable = object : VersionedEntitySqlTable<AT>("at", ATTable, atDbKeyFactory, dp) {
            override fun load(ctx: DSLContext, record: Record): AT {
                throw UnsupportedOperationException("AT cannot be created with atTable.load")
            }

            override fun save(ctx: DSLContext, at: AT) {
                saveAT(ctx, at)
            }

            override fun defaultSort(): Collection<SortField<*>> {
                return listOf(tableClass.field("id", Long::class.java).asc())
            }
        }

        atStateTable =
            object : VersionedEntitySqlTable<AT.ATState>("at_state", AT_STATE, atStateDbKeyFactory, dp) {
                override fun load(ctx: DSLContext, record: Record): AT.ATState {
                    return SqlATState(dp, record)
                }

                override fun save(ctx: DSLContext, atState: AT.ATState) {
                    saveATState(ctx, atState)
                }

                override fun defaultSort(): Collection<SortField<*>> {
                    return listOf(
                        tableClass.field("prev_height", Int::class.java).asc(),
                        heightField.asc(),
                        tableClass.field("at_id", Long::class.java).asc()
                    )
                }
            }
    }

    private fun saveATState(ctx: DSLContext, atState: AT.ATState) {
        var record = AtStateRecord()
        record.atId = atState.atId
        record.state = AT.compressState(atState.state)
        record.prevHeight = atState.prevHeight
        record.nextHeight = atState.nextHeight
        record.sleepBetween = atState.sleepBetween
        record.prevBalance = atState.prevBalance
        record.freezeWhenSameBalance = atState.freezeWhenSameBalance
        record.minActivateAmount = atState.minActivationAmount
        record.height = dp.blockchainService.height
        record.latest = true
        ctx.upsert(record, AT_STATE.AT_ID, AT_STATE.HEIGHT).execute()
    }

    private fun saveAT(ctx: DSLContext, at: AT) {
        ctx.insertInto(
            ATTable,
            ATTable.ID, ATTable.CREATOR_ID, ATTable.NAME, ATTable.DESCRIPTION,
            ATTable.VERSION, ATTable.CSIZE, ATTable.DSIZE, ATTable.C_USER_STACK_BYTES,
            ATTable.C_CALL_STACK_BYTES, ATTable.CREATION_HEIGHT,
            ATTable.AP_CODE, ATTable.HEIGHT
        ).values(
            at.id, at.creator, at.name, at.description,
            at.version, at.cSize, at.dSize, at.cUserStackBytes,
            at.cCallStackBytes, at.creationBlockHeight,
            AT.compressState(at.apCodeBytes), dp.blockchainService.height
        ).execute()
    }

    override fun isATAccountId(id: Long?): Boolean {
        return dp.db.getUsingDslContext { ctx -> ctx.fetchExists(ctx.selectOne().from(ATTable).where(ATTable.ID.eq(id)).and(ATTable.LATEST.isTrue)) }
    }

    override fun getAT(id: Long?): AT? {
        return dp.db.getUsingDslContext { ctx ->
            val record = ctx.select(*ATTable.fields())
                .select(*AT_STATE.fields())
                .from(ATTable.join(AT_STATE).on(ATTable.ID.eq(AT_STATE.AT_ID)))
                .where(
                    ATTable.LATEST.isTrue
                        .and(AT_STATE.LATEST.isTrue)
                        .and(ATTable.ID.eq(id))
                )
                .fetchOne() ?: return@getUsingDslContext null

            val at = record.into(ATTable)
            val atState = record.into(AT_STATE)

            createAT(dp, at, atState)
        }
    }

    private fun createAT(dp: DependencyProvider, at: AtRecord, atState: AtStateRecord): AT {
        return AT(
            dp,
            at.id,
            at.creatorId,
            at.name,
            at.description,
            at.version!!,
            AT.decompressState(atState.state)!!,
            at.csize!!,
            at.dsize!!,
            at.cUserStackBytes!!,
            at.cCallStackBytes!!,
            at.creationHeight!!,
            atState.sleepBetween!!,
            atState.nextHeight!!,
            atState.freezeWhenSameBalance!!,
            atState.minActivateAmount!!,
            AT.decompressState(at.apCode)!!
        )
    }

    override fun getATsIssuedBy(accountId: Long?): List<Long> {
        return dp.db.getUsingDslContext<List<Long>> { ctx ->
            ctx.selectFrom(ATTable).where(ATTable.LATEST.isTrue).and(ATTable.CREATOR_ID.eq(accountId))
                .orderBy(ATTable.CREATION_HEIGHT.desc(), ATTable.ID.asc()).fetch().getValues(ATTable.ID)
        }
    }

    override fun findTransaction(startHeight: Int, endHeight: Int, atID: Long?, numOfTx: Int, minAmount: Long): Long? {
        return dp.db.getUsingDslContext<Long> { ctx ->
            val query = ctx.select(TRANSACTION.ID)
                .from(TRANSACTION)
                .where(TRANSACTION.HEIGHT.between(startHeight, endHeight - 1))
                .and(TRANSACTION.RECIPIENT_ID.eq(atID))
                .and(TRANSACTION.AMOUNT.greaterOrEqual(minAmount))
                .orderBy(TRANSACTION.HEIGHT, TRANSACTION.ID)
                .query
            SqlDbUtils.applyLimits(query, numOfTx, numOfTx + 1)
            val result = query.fetch()
            if (result.isEmpty()) 0L else result[0].value1()
        }
    }

    override fun findTransactionHeight(transactionId: Long?, height: Int, atID: Long?, minAmount: Long): Int {
        return dp.db.getUsingDslContext { ctx ->
            try {
                val fetch = ctx.select(TRANSACTION.ID)
                    .from(TRANSACTION)
                    .where(TRANSACTION.HEIGHT.eq(height))
                    .and(TRANSACTION.RECIPIENT_ID.eq(atID))
                    .and(TRANSACTION.AMOUNT.greaterOrEqual(minAmount))
                    .orderBy(TRANSACTION.HEIGHT, TRANSACTION.ID)
                    .fetch()
                    .iterator()
                var counter = 0
                while (fetch.hasNext()) {
                    counter++
                    val currentTransactionId = fetch.next().value1()
                    if (currentTransactionId == transactionId) break
                }
                return@getUsingDslContext counter
            } catch (e: DataAccessException) {
                throw RuntimeException(e.toString(), e)
            }
        }
    }

    internal inner class SqlATState internal constructor(dp: DependencyProvider, record: Record) : AT.ATState(
        dp,
        record.get(AT_STATE.AT_ID),
        record.get(AT_STATE.STATE),
        record.get(AT_STATE.NEXT_HEIGHT),
        record.get(AT_STATE.SLEEP_BETWEEN),
        record.get(AT_STATE.PREV_BALANCE),
        record.get(AT_STATE.FREEZE_WHEN_SAME_BALANCE),
        record.get(AT_STATE.MIN_ACTIVATE_AMOUNT)
    )
}
