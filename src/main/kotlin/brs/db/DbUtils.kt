package brs.db

import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Query
import org.jooq.UpdatableRecord
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Use the DSL Context of the DB and return a value
 * @param action The action to perform using the DSL Context, which returns a value
 * @return The value fetched by the `action`
 */
@UseExperimental(ExperimentalContracts::class)
inline fun <T> Db.getUsingDslContext(action: (DSLContext) -> T): T {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    getDslContext().use { context -> return action(context) }
}

/**
 * Use the DSL Context of the DB
 * @param action The action to perform using the DSL Context
 */
@UseExperimental(ExperimentalContracts::class)
inline fun Db.useDslContext(action: (DSLContext) -> Unit) {
    contract { callsInPlace(action, InvocationKind.EXACTLY_ONCE) }
    getDslContext().use { context -> action(context) }
}

fun DSLContext.upsert(record: UpdatableRecord<*>, vararg keys: Field<*>): Query {
    return insertInto(record.getTable())
        .set(record)
        .onConflict(*keys) // TODO work around having to use spread operator here...
        .doUpdate()
        .set(record)
}
