package brs.db.sql

import brs.db.BurstKey
import brs.db.VersionedBatchEntityTable
import brs.db.assertInTransaction
import brs.db.useDslContext
import brs.entity.DependencyProvider
import org.ehcache.Cache
import org.jooq.*
import org.jooq.impl.DSL

internal abstract class SqlVersionedBatchEntityTable<T> internal constructor(
    table: Table<*>,
    heightField: Field<Int>,
    latestField: Field<Boolean>,
    dbKeyFactory: SqlDbKey.Factory<T>,
    private val tClass: Class<T>,
    private val dp: DependencyProvider
) : SqlVersionedEntityTable<T>(table, heightField, latestField, dbKeyFactory, dp), VersionedBatchEntityTable<T> {
    override val count: Int
        get() {
            assertNotInTransaction()
            return super.count
        }

    override val rowCount: Int
        get() {
            assertNotInTransaction()
            return super.rowCount
        }

    private val batch: MutableMap<BurstKey, T>
        get() = dp.db.getBatch(table)

    private val batchCache: Cache<BurstKey, T>
        get() = dp.dbCacheService.getCache(tableName, tClass)!!

    private fun assertNotInTransaction() {
        check(!dp.db.isInTransaction()) { "Cannot use batch table during transaction" }
    }

    protected abstract fun bulkUpsert(ctx: DSLContext, entities: Collection<T>)

    override fun delete(t: T): Boolean {
        dp.db.assertInTransaction()
        val dbKey = dbKeyFactory.newKey(t) as SqlDbKey
        batchCache.remove(dbKey)
        batch.remove(dbKey)
        return true
    }

    override fun save(ctx: DSLContext, entity: T) {
        insert(entity)
    }

    override fun get(dbKey: BurstKey): T? {
        if (batchCache.containsKey(dbKey)) {
            return batchCache.get(dbKey)
        } else if (dp.db.isInTransaction() && batch.containsKey(dbKey)) {
            return batch[dbKey]
        }
        val item = super.get(dbKey)
        if (item != null) {
            batchCache.put(dbKey, item)
        }
        return item
    }

    override fun insert(entity: T) {
        dp.db.assertInTransaction()
        val key = dbKeyFactory.newKey(entity)
        batch[key] = entity
        batchCache.put(key, entity)
    }

    override fun finish() {
        dp.db.assertInTransaction()
        if (batch.keys.isEmpty()) {
            return
        }
        val keySet = batch.keys

        dp.db.useDslContext { ctx ->
            // keySet chunked due:
            // [SQLITE_ERROR] SQL error or missing database (Expression tree is too large (maximum depth 1000)
            for (keySetChunk in keySet.chunked(990)) {
                val updateQuery = ctx.updateQuery(table)
                updateQuery.addConditions(latestField?.isTrue)
                updateQuery.addValue(latestField, false)
                var accountsCondition = DSL.noCondition()
                for (dbKey in keySetChunk) {
                    var pkCondition = DSL.noCondition()
                    for ((index, idColumn) in dbKeyFactory.pkColumns.withIndex()) {
                        pkCondition = pkCondition
                            .and(table.field(idColumn, Long::class.java).eq(dbKey.pkValues[index]))
                    }
                    accountsCondition = accountsCondition.or(pkCondition)
                }
                updateQuery.addConditions(accountsCondition)
                ctx.execute(updateQuery)
            }
            bulkUpsert(ctx, batch.values)
            batch.clear()
        }
    }

    override fun get(dbKey: BurstKey, height: Int): T? {
        assertNotInTransaction()
        return super.get(dbKey, height)
    }

    override fun getBy(condition: Condition): T? {
        assertNotInTransaction()
        return super.getBy(condition)
    }

    override fun getBy(condition: Condition, height: Int): T? {
        assertNotInTransaction()
        return super.getBy(condition, height)
    }

    override fun getManyBy(condition: Condition, from: Int, to: Int, sort: Collection<SortField<*>>): Collection<T> {
        assertNotInTransaction()
        return super.getManyBy(condition, from, to, sort)
    }

    override fun getManyBy(
        condition: Condition,
        height: Int,
        from: Int,
        to: Int,
        sort: Collection<SortField<*>>
    ): Collection<T> {
        assertNotInTransaction()
        return super.getManyBy(condition, height, from, to, sort)
    }

    override fun getManyBy(ctx: DSLContext, query: SelectQuery<out Record>, cache: Boolean): Collection<T> {
        assertNotInTransaction()
        return super.getManyBy(ctx, query, cache)
    }

    override fun getAll(from: Int, to: Int, sort: Collection<SortField<*>>): Collection<T> {
        assertNotInTransaction()
        return super.getAll(from, to, sort)
    }

    override fun getAll(height: Int, from: Int, to: Int, sort: Collection<SortField<*>>): Collection<T> {
        assertNotInTransaction()
        return super.getAll(height, from, to, sort)
    }

    override fun rollback(height: Int) {
        super.rollback(height)
        batch.clear()
    }
}
