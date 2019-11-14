package brs.db.sql

import brs.db.BurstKey
import brs.db.EscrowStore
import brs.db.VersionedEntityTable
import brs.db.upsert
import brs.entity.DependencyProvider
import brs.entity.Escrow
import brs.entity.Transaction
import brs.schema.Tables.ESCROW
import brs.schema.Tables.ESCROW_DECISION
import brs.schema.tables.records.EscrowDecisionRecord
import brs.schema.tables.records.EscrowRecord
import org.jooq.DSLContext
import org.jooq.Record

internal class SqlEscrowStore(private val dp: DependencyProvider) : EscrowStore {
    override val escrowDbKeyFactory = object : SqlDbKey.LongKeyFactory<Escrow>(ESCROW.ID) {
        override fun newKey(escrow: Escrow): BurstKey {
            return escrow.dbKey
        }
    }

    override val escrowTable: VersionedEntityTable<Escrow>
    override val decisionDbKeyFactory = object : SqlDbKey.LinkKeyFactory<Escrow.Decision>("escrow_id", "account_id") {
        override fun newKey(decision: Escrow.Decision): BurstKey {
            return decision.dbKey
        }
    }
    override val decisionTable: VersionedEntityTable<Escrow.Decision>
    override val resultTransactions = mutableListOf<Transaction>()

    init {
        escrowTable = object : VersionedEntitySqlTable<Escrow>("escrow", ESCROW, escrowDbKeyFactory, dp) {
            override fun load(ctx: DSLContext, rs: Record): Escrow {
                return SqlEscrow(rs)
            }

            override fun save(ctx: DSLContext, escrow: Escrow) {
                saveEscrow(ctx, escrow)
            }
        }

        decisionTable = object :
            VersionedEntitySqlTable<Escrow.Decision>("escrow_decision", ESCROW_DECISION, decisionDbKeyFactory, dp) {
            override fun load(ctx: DSLContext, record: Record): Escrow.Decision {
                return SqlDecision(record)
            }

            override fun save(ctx: DSLContext, decision: Escrow.Decision) {
                saveDecision(ctx, decision)
            }
        }
    }

    private fun saveDecision(ctx: DSLContext, decision: Escrow.Decision) {
        val record = EscrowDecisionRecord()
        record.escrowId = decision.escrowId
        record.accountId = decision.accountId
        record.decision = Escrow.decisionToByte(decision.decision!!).toInt()
        record.height = dp.blockchainService.height
        record.latest = true
        ctx.upsert(record, ESCROW_DECISION.ESCROW_ID, ESCROW_DECISION.ACCOUNT_ID, ESCROW_DECISION.HEIGHT).execute()
    }

    override fun getEscrowTransactionsByParticipant(accountId: Long?): Collection<Escrow> {
        val filtered = mutableListOf<Escrow>()
        for (decision in decisionTable.getManyBy(ESCROW_DECISION.ACCOUNT_ID.eq(accountId), 0, -1)) {
            val escrow = escrowTable[escrowDbKeyFactory.newKey(decision.escrowId!!)]
            if (escrow != null) {
                filtered.add(escrow)
            }
        }
        return filtered
    }

    private fun saveEscrow(ctx: DSLContext, escrow: Escrow) {
        val record = EscrowRecord()
        record.id = escrow.id
        record.senderId = escrow.senderId
        record.recipientId = escrow.recipientId
        record.amount = escrow.amountPlanck
        record.requiredSigners = escrow.requiredSigners
        record.deadline = escrow.deadline
        record.deadlineAction = Escrow.decisionToByte(escrow.deadlineAction).toInt()
        record.height = dp.blockchainService.height
        record.latest = true
        ctx.upsert(record, ESCROW.ID, ESCROW.HEIGHT).execute()
    }

    private inner class SqlDecision internal constructor(record: Record) : Escrow.Decision(
        decisionDbKeyFactory.newKey(
            record.get(ESCROW_DECISION.ESCROW_ID),
            record.get(ESCROW_DECISION.ACCOUNT_ID)
        ),
        record.get(ESCROW_DECISION.ESCROW_ID),
        record.get(ESCROW_DECISION.ACCOUNT_ID),
        Escrow.byteToDecision(record.get(ESCROW_DECISION.DECISION).toByte())
    )

    private inner class SqlEscrow internal constructor(record: Record) : Escrow(
        dp,
        record.get(ESCROW.ID),
        record.get(ESCROW.SENDER_ID),
        record.get(ESCROW.RECIPIENT_ID),
        escrowDbKeyFactory.newKey(record.get(ESCROW.ID)),
        record.get(ESCROW.AMOUNT),
        record.get(ESCROW.REQUIRED_SIGNERS),
        record.get(ESCROW.DEADLINE),
        byteToDecision(record.get(ESCROW.DEADLINE_ACTION).toByte())!!
    )

    override fun getDecisions(id: Long?): Collection<Escrow.Decision> {
        return decisionTable.getManyBy(ESCROW_DECISION.ESCROW_ID.eq(id), 0, -1)
    }
}
