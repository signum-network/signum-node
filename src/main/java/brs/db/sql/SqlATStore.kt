package brs.db.sql

import brs.DependencyProvider
import brs.at.AtApiHelper
import brs.at.AtConstants
import brs.db.BurstKey
import brs.db.VersionedEntityTable
import brs.db.store.ATStore
import brs.schema.Tables.*
import brs.schema.tables.records.AtRecord
import brs.schema.tables.records.AtStateRecord
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SortField
import org.jooq.exception.DataAccessException

class SqlATStore(private val dp: DependencyProvider) : ATStore {

    override val atDbKeyFactory: BurstKey.LongKeyFactory<brs.at.AT> = object : DbKey.LongKeyFactory<brs.at.AT>(AT.ID) {
        override fun newKey(at: brs.at.AT): BurstKey {
            return at.dbKey
        }
    }
    override val atTable: VersionedEntityTable<brs.at.AT>

    override val atStateDbKeyFactory: BurstKey.LongKeyFactory<brs.at.AT.ATState> = object : DbKey.LongKeyFactory<brs.at.AT.ATState>(AT_STATE.AT_ID) {
        override fun newKey(atState: brs.at.AT.ATState): BurstKey {
            return atState.dbKey
        }
    }

    override val atStateTable: VersionedEntityTable<brs.at.AT.ATState>

    override val orderedATs: List<Long>
        get() = dp.db.useDslContext<List<Long>> { ctx ->
            ctx.selectFrom(AT.join(AT_STATE).on(AT.ID.eq(AT_STATE.AT_ID)).join(ACCOUNT).on(AT.ID.eq(ACCOUNT.ID)))
                    .where(AT.LATEST.isTrue)
                    .and(AT_STATE.LATEST.isTrue)
                    .and(ACCOUNT.LATEST.isTrue)
                    .and(AT_STATE.NEXT_HEIGHT.lessOrEqual(dp.blockchain.height + 1))
                    .and(ACCOUNT.BALANCE.greaterOrEqual(dp.atConstants.stepFee(dp.blockchain.height) * dp.atConstants.apiStepMultiplier(dp.blockchain.height)))
                    .and(AT_STATE.FREEZE_WHEN_SAME_BALANCE.isFalse.or("account.balance - at_state.prev_balance >= at_state.min_activate_amount"))
                    .orderBy(AT_STATE.PREV_HEIGHT.asc(), AT_STATE.NEXT_HEIGHT.asc(), AT.ID.asc())
                    .fetch()
                    .getValues(AT.ID)
        }

    override val allATIds: Collection<Long>
        get() = dp.db.useDslContext<List<Long>> { ctx -> ctx.selectFrom(AT).where(AT.LATEST.isTrue).fetch().getValues(AT.ID) }

    init {
        atTable = object : VersionedEntitySqlTable<brs.at.AT>("at", AT, atDbKeyFactory, dp) {
            override fun load(ctx: DSLContext, rs: Record): brs.at.AT {
                throw RuntimeException("AT attempted to be created with atTable.load")
            }

            override fun save(ctx: DSLContext, at: brs.at.AT) {
                saveAT(ctx, at)
            }

            override fun defaultSort(): List<SortField<*>> {
                val sort = mutableListOf<SortField<*>>()
                sort.add(tableClass.field("id", Long::class.java).asc())
                return sort
            }
        }

        atStateTable = object : VersionedEntitySqlTable<brs.at.AT.ATState>("at_state", brs.schema.Tables.AT_STATE, atStateDbKeyFactory, dp) {
            override fun load(ctx: DSLContext, rs: Record): brs.at.AT.ATState {
                return SqlATState(dp, rs)
            }

            override fun save(ctx: DSLContext, atState: brs.at.AT.ATState) {
                saveATState(ctx, atState)
            }

            override fun defaultSort(): List<SortField<*>> {
                val sort = mutableListOf<SortField<*>>()
                sort.add(tableClass.field("prev_height", Int::class.java).asc())
                sort.add(heightField.asc())
                sort.add(tableClass.field("at_id", Long::class.java).asc())
                return sort
            }
        }
    }

    private fun saveATState(ctx: DSLContext, atState: brs.at.AT.ATState) {
        ctx.mergeInto(AT_STATE, AT_STATE.AT_ID, AT_STATE.STATE, AT_STATE.PREV_HEIGHT, AT_STATE.NEXT_HEIGHT, AT_STATE.SLEEP_BETWEEN, AT_STATE.PREV_BALANCE, AT_STATE.FREEZE_WHEN_SAME_BALANCE, AT_STATE.MIN_ACTIVATE_AMOUNT, AT_STATE.HEIGHT, AT_STATE.LATEST)
                .key(AT_STATE.AT_ID, AT_STATE.HEIGHT)
                .values(atState.atId, brs.at.AT.compressState(atState.state), atState.prevHeight, atState.nextHeight, atState.sleepBetween, atState.prevBalance, atState.freezeWhenSameBalance, atState.minActivationAmount, dp.blockchain.height, true)
                .execute()
    }

    private fun saveAT(ctx: DSLContext, at: brs.at.AT) {
        ctx.insertInto(
                AT,
                AT.ID, AT.CREATOR_ID, AT.NAME, AT.DESCRIPTION,
                AT.VERSION, AT.CSIZE, AT.DSIZE, AT.C_USER_STACK_BYTES,
                AT.C_CALL_STACK_BYTES, AT.CREATION_HEIGHT,
                AT.AP_CODE, AT.HEIGHT
        ).values(
                AtApiHelper.getLong(at.id!!), AtApiHelper.getLong(at.creator!!), at.name, at.description,
                at.version, at.getcSize(), at.getdSize(), at.getcUserStackBytes(),
                at.getcCallStackBytes(), at.creationBlockHeight,
                brs.at.AT.compressState(at.apCodeBytes), dp.blockchain.height
        ).execute()
    }

    override fun isATAccountId(id: Long?): Boolean {
        return dp.db.useDslContext<Boolean> { ctx -> ctx.fetchExists(ctx.selectOne().from(AT).where(AT.ID.eq(id)).and(AT.LATEST.isTrue)) }
    }

    override fun getAT(id: Long?): brs.at.AT? {
        return dp.db.useDslContext<brs.at.AT?> { ctx ->
            val record = ctx.select(*AT.fields())
                    .select(*AT_STATE.fields())
                    .from(AT.join(AT_STATE).on(AT.ID.eq(AT_STATE.AT_ID)))
                    .where(AT.LATEST.isTrue
                            .and(AT_STATE.LATEST.isTrue)
                            .and(AT.ID.eq(id)))
                    .fetchOne() ?: return@useDslContext null

            val at = record!!.into(AT)
            val atState = record.into(AT_STATE)

            createAT(dp, at, atState)
        }
    }

    private fun createAT(dp: DependencyProvider, at: AtRecord, atState: AtStateRecord): brs.at.AT {
        return brs.at.AT(dp, AtApiHelper.getByteArray(at.id!!), AtApiHelper.getByteArray(at.creatorId!!), at.name, at.description, at.version!!,
                brs.at.AT.decompressState(atState.state)!!, at.csize!!, at.dsize!!, at.cUserStackBytes!!, at.cCallStackBytes!!, at.creationHeight!!, atState.sleepBetween!!, atState.nextHeight!!,
                atState.freezeWhenSameBalance!!, atState.minActivateAmount!!, brs.at.AT.decompressState(at.apCode)!!)
    }

    override fun getATsIssuedBy(accountId: Long?): List<Long> {
        return dp.db.useDslContext<List<Long>> { ctx -> ctx.selectFrom(AT).where(AT.LATEST.isTrue).and(AT.CREATOR_ID.eq(accountId)).orderBy(AT.CREATION_HEIGHT.desc(), AT.ID.asc()).fetch().getValues(AT.ID) }
    }

    override fun findTransaction(startHeight: Int, endHeight: Int, atID: Long?, numOfTx: Int, minAmount: Long): Long? {
        return dp.db.useDslContext<Long> { ctx ->
            val query = ctx.select(TRANSACTION.ID)
                    .from(TRANSACTION)
                    .where(TRANSACTION.HEIGHT.between(startHeight, endHeight - 1))
                    .and(TRANSACTION.RECIPIENT_ID.eq(atID))
                    .and(TRANSACTION.AMOUNT.greaterOrEqual(minAmount))
                    .orderBy(TRANSACTION.HEIGHT, TRANSACTION.ID)
                    .query
            DbUtils.applyLimits(query, numOfTx, numOfTx + 1)
            val result = query.fetch()
            if (result.isEmpty()) 0L else result[0].value1()
        }
    }

    override fun findTransactionHeight(transactionId: Long?, height: Int, atID: Long?, minAmount: Long): Int {
        return dp.db.useDslContext<Int> { ctx ->
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
                return@useDslContext counter
            } catch (e: DataAccessException) {
                throw RuntimeException(e.toString(), e)
            }
        }
    }

    internal inner class SqlATState internal constructor(dp: DependencyProvider, record: Record) : brs.at.AT.ATState(dp, record.get(AT_STATE.AT_ID), record.get(AT_STATE.STATE), record.get(AT_STATE.NEXT_HEIGHT), record.get(AT_STATE.SLEEP_BETWEEN), record.get(AT_STATE.PREV_BALANCE), record.get(AT_STATE.FREEZE_WHEN_SAME_BALANCE), record.get(AT_STATE.MIN_ACTIVATE_AMOUNT))
}