package brs.db.sql

import brs.db.BurstKey
import brs.db.IndirectIncomingStore
import brs.db.upsert
import brs.db.useDslContext
import brs.entity.DependencyProvider
import brs.entity.IndirectIncoming
import brs.schema.Tables.INDIRECT_INCOMING
import brs.schema.tables.records.IndirectIncomingRecord
import org.jooq.DSLContext
import org.jooq.Query
import org.jooq.Record

internal class SqlIndirectIncomingStore(private val dp: DependencyProvider) : IndirectIncomingStore {

    private val indirectIncomingTable: EntitySqlTable<IndirectIncoming>

    init {
        val indirectIncomingDbKeyFactory =
            object : SqlDbKey.LinkKeyFactory<IndirectIncoming>("account_id", "transaction_id") {
                override fun newKey(indirectIncoming: IndirectIncoming): BurstKey {
                    return newKey(indirectIncoming.accountId, indirectIncoming.transactionId)
                }
            }

        this.indirectIncomingTable = object :
            EntitySqlTable<IndirectIncoming>("indirect_incoming", INDIRECT_INCOMING, indirectIncomingDbKeyFactory, dp) {
            override fun load(ctx: DSLContext, rs: Record): IndirectIncoming {
                return IndirectIncoming(
                    rs.get(INDIRECT_INCOMING.ACCOUNT_ID),
                    rs.get(INDIRECT_INCOMING.TRANSACTION_ID),
                    rs.get(INDIRECT_INCOMING.HEIGHT)
                )
            }

            private fun getQuery(ctx: DSLContext, indirectIncoming: IndirectIncoming): Query {
                val record = IndirectIncomingRecord()
                record.accountId = indirectIncoming.accountId
                record.transactionId = indirectIncoming.transactionId
                record.height = indirectIncoming.height
                return ctx.upsert(record, INDIRECT_INCOMING.ACCOUNT_ID, INDIRECT_INCOMING.TRANSACTION_ID)
            }

            override fun save(ctx: DSLContext, indirectIncoming: IndirectIncoming) {
                getQuery(ctx, indirectIncoming).execute()
            }

            override fun save(ctx: DSLContext, indirectIncomings: Array<IndirectIncoming>) {
                val queries = mutableListOf<Query>()
                for (indirectIncoming in indirectIncomings) {
                    queries.add(getQuery(ctx, indirectIncoming))
                }
                ctx.batch(queries).execute()
            }
        }
    }

    override fun addIndirectIncomings(indirectIncomings: Collection<IndirectIncoming>) {
        dp.db.useDslContext { ctx -> indirectIncomingTable.save(ctx, indirectIncomings.toTypedArray()) }
    }

    override fun getIndirectIncomings(accountId: Long, from: Int, to: Int): List<Long> {
        return indirectIncomingTable.getManyBy(INDIRECT_INCOMING.ACCOUNT_ID.eq(accountId), from, to)
            .map { it.transactionId }
    }
}
