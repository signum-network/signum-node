package brs.db.sql

import brs.db.*
import brs.entity.Account
import brs.entity.DependencyProvider
import brs.schema.Tables.*
import brs.schema.tables.records.AccountAssetRecord
import brs.schema.tables.records.AccountRecord
import brs.schema.tables.records.RewardRecipAssignRecord
import brs.util.convert.toUnsignedString
import brs.util.logging.safeInfo
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SortField
import org.slf4j.LoggerFactory

internal class SqlAccountStore(private val dp: DependencyProvider) : AccountStore {

    override val accountAssetTable: VersionedEntityTable<Account.AccountAsset>

    override val rewardRecipientAssignmentTable: VersionedEntityTable<Account.RewardRecipientAssignment>

    override val accountTable: VersionedBatchEntityTable<Account>

    override val rewardRecipientAssignmentKeyFactory: BurstKey.LongKeyFactory<Account.RewardRecipientAssignment>
        get() = rewardRecipientAssignmentDbKeyFactory

    override val accountAssetKeyFactory: BurstKey.LinkKeyFactory<Account.AccountAsset>
        get() = accountAssetDbKeyFactory

    override val accountKeyFactory: BurstKey.LongKeyFactory<Account>
        get() = accountDbKeyFactory

    init {
        rewardRecipientAssignmentTable = object : VersionedEntitySqlTable<Account.RewardRecipientAssignment>(
            "reward_recip_assign",
            REWARD_RECIP_ASSIGN,
            rewardRecipientAssignmentDbKeyFactory,
            dp
        ) {

            override fun load(ctx: DSLContext, record: Record): Account.RewardRecipientAssignment {
                return SqlRewardRecipientAssignment(record)
            }

            override fun save(ctx: DSLContext, assignment: Account.RewardRecipientAssignment) {
                val record = RewardRecipAssignRecord()
                record.accountId = assignment.accountId
                record.prevRecipId = assignment.prevRecipientId
                record.recipId = assignment.recipientId
                record.fromHeight = assignment.fromHeight
                record.height = dp.blockchainService.height
                record.latest = true
                ctx.upsert(
                    record, REWARD_RECIP_ASSIGN.ACCOUNT_ID, REWARD_RECIP_ASSIGN.HEIGHT
                )
                    .execute()
            }
        }

        accountAssetTable = object : VersionedEntitySqlTable<Account.AccountAsset>(
            "account_asset",
            ACCOUNT_ASSET,
            accountAssetDbKeyFactory,
            dp
        ) {
            private val sort = initializeSort()

            private fun initializeSort(): List<SortField<*>> {
                return listOf<SortField<*>>(
                    tableClass.field("quantity", Long::class.java).desc(),
                    tableClass.field("account_id", Long::class.java).asc(),
                    tableClass.field("asset_id", Long::class.java).asc()
                )
            }

            override fun load(ctx: DSLContext, record: Record): Account.AccountAsset {
                return SQLAccountAsset(record)
            }

            override fun save(ctx: DSLContext, accountAsset: Account.AccountAsset) {
                val record = AccountAssetRecord()
                record.accountId = accountAsset.accountId
                record.assetId = accountAsset.assetId
                record.quantity = accountAsset.quantity
                record.unconfirmedQuantity = accountAsset.unconfirmedQuantity
                record.height = dp.blockchainService.height
                record.latest = true
                ctx.upsert(record, ACCOUNT_ASSET.ACCOUNT_ID, ACCOUNT_ASSET.ASSET_ID, ACCOUNT_ASSET.HEIGHT)
                    .execute()
            }

            override fun defaultSort(): Collection<SortField<*>> {
                return sort
            }
        }

        accountTable = object :
            VersionedBatchEntitySqlTable<Account>("account", ACCOUNT, accountDbKeyFactory, Account::class.java, dp) {
            override fun load(ctx: DSLContext, record: Record): Account {
                return SqlAccount(record)
            }

            override fun bulkInsert(ctx: DSLContext, accounts: Collection<Account>) {
                val height = dp.blockchainService.height
                ctx.batch(accounts.map { account ->
                    val record = AccountRecord()
                    record.id = account.id
                    record.height = height
                    record.creationHeight = account.creationHeight
                    record.publicKey = account.publicKey
                    record.keyHeight = account.keyHeight
                    record.balance = account.balancePlanck
                    record.unconfirmedBalance = account.unconfirmedBalancePlanck
                    record.forgedBalance = account.forgedBalancePlanck
                    record.name = account.name
                    record.description = account.description
                    record.latest = true
                    ctx.upsert(record, ACCOUNT.ID, ACCOUNT.HEIGHT)
                }).execute()
            }
        }
    }

    override fun getAssetAccountsCount(assetId: Long): Int {
        return dp.db.getUsingDslContext<Int> { ctx ->
            ctx.selectCount().from(ACCOUNT_ASSET).where(ACCOUNT_ASSET.ASSET_ID.eq(assetId))
                .and(ACCOUNT_ASSET.LATEST.isTrue).fetchOne(0, Int::class.javaPrimitiveType)
        }
    }

    override fun getAccountsWithRewardRecipient(recipientId: Long?): Collection<Account.RewardRecipientAssignment> {
        return rewardRecipientAssignmentTable.getManyBy(
            getAccountsWithRewardRecipientClause(
                recipientId!!,
                dp.blockchainService.height + 1
            ), 0, -1
        )
    }

    override fun getAssets(from: Int, to: Int, id: Long?): Collection<Account.AccountAsset> {
        return accountAssetTable.getManyBy(ACCOUNT_ASSET.ACCOUNT_ID.eq(id), from, to)
    }

    override fun getAssetAccounts(assetId: Long, from: Int, to: Int): Collection<Account.AccountAsset> {
        val sort = listOf(
            ACCOUNT_ASSET.field("quantity", Long::class.java).desc(),
            ACCOUNT_ASSET.field("account_id", Long::class.java).asc()
        )
        return accountAssetTable.getManyBy(ACCOUNT_ASSET.ASSET_ID.eq(assetId), from, to, sort)
    }

    override fun getAssetAccounts(assetId: Long, height: Int, from: Int, to: Int): Collection<Account.AccountAsset> {
        if (height < 0) {
            return getAssetAccounts(assetId, from, to)
        }

        val sort = listOf<SortField<*>>(
            ACCOUNT_ASSET.field("quantity", Long::class.java).desc(),
            ACCOUNT_ASSET.field("account_id", Long::class.java).asc()
        )
        return accountAssetTable.getManyBy(ACCOUNT_ASSET.ASSET_ID.eq(assetId), height, from, to, sort)
    }

    override fun setOrVerify(account: Account, key: ByteArray, height: Int): Boolean {
        return when {
            account.publicKey == null -> {
                if (dp.db.isInTransaction()) {
                    account.publicKey = key
                    account.keyHeight = -1
                    accountTable.insert(account)
                }
                true
            }
            account.publicKey!!.contentEquals(key) -> return true
            account.keyHeight == -1 -> {
                logger.safeInfo { "DUPLICATE KEY!!!" }
                logger.safeInfo { "Account key for ${account.id.toUnsignedString()} was already set to a different one at the same height, current height is $height, rejecting new key" }
                false
            }
            account.keyHeight >= height -> {
                logger.safeInfo { "DUPLICATE KEY!!!" }
                if (dp.db.isInTransaction()) {
                    logger.safeInfo { "Changing key for account ${account.id.toUnsignedString()} at height $height, was previously set to a different one at height ${account.keyHeight}" }
                    account.publicKey = key
                    account.keyHeight = height
                    accountTable.insert(account)
                }
                true
            }
            else -> {
                logger.safeInfo { "DUPLICATE KEY!!!" }
                logger.safeInfo { "Invalid key for account ${account.id.toUnsignedString()} at height $height, was already set to a different one at height ${account.keyHeight}" }
                false
            }
        }
    }

    internal class SQLAccountAsset(rs: Record) : Account.AccountAsset(
        rs.get(ACCOUNT_ASSET.ACCOUNT_ID),
        rs.get(ACCOUNT_ASSET.ASSET_ID),
        rs.get(ACCOUNT_ASSET.QUANTITY),
        rs.get(ACCOUNT_ASSET.UNCONFIRMED_QUANTITY),
        accountAssetDbKeyFactory.newKey(rs.get(ACCOUNT_ASSET.ACCOUNT_ID), rs.get(ACCOUNT_ASSET.ASSET_ID))
    )

    internal inner class SqlAccount(record: Record) : Account(
        record.get(ACCOUNT.ID),
        accountDbKeyFactory.newKey(record.get(ACCOUNT.ID)),
        record.get(ACCOUNT.CREATION_HEIGHT)
    ) {
        init {
            this.publicKey = record.get(ACCOUNT.PUBLIC_KEY)
            this.keyHeight = record.get(ACCOUNT.KEY_HEIGHT)
            this.balancePlanck = record.get(ACCOUNT.BALANCE)
            this.unconfirmedBalancePlanck = record.get(ACCOUNT.UNCONFIRMED_BALANCE)
            this.forgedBalancePlanck = record.get(ACCOUNT.FORGED_BALANCE)
            this.name = record.get(ACCOUNT.NAME)
            this.description = record.get(ACCOUNT.DESCRIPTION)
        }
    }

    internal inner class SqlRewardRecipientAssignment(record: Record) : Account.RewardRecipientAssignment(
        record.get(REWARD_RECIP_ASSIGN.ACCOUNT_ID),
        record.get(REWARD_RECIP_ASSIGN.PREV_RECIP_ID),
        record.get(REWARD_RECIP_ASSIGN.RECIP_ID),
        record.get(REWARD_RECIP_ASSIGN.FROM_HEIGHT),
        rewardRecipientAssignmentDbKeyFactory.newKey(record.get(REWARD_RECIP_ASSIGN.ACCOUNT_ID))
    )

    companion object {

        private val accountDbKeyFactory = object : SqlDbKey.LongKeyFactory<Account>(ACCOUNT.ID) {
            override fun newKey(account: Account): SqlDbKey {
                return account.nxtKey as SqlDbKey
            }
        }
        private val rewardRecipientAssignmentDbKeyFactory =
            object : SqlDbKey.LongKeyFactory<Account.RewardRecipientAssignment>(REWARD_RECIP_ASSIGN.ACCOUNT_ID) {
                override fun newKey(assignment: Account.RewardRecipientAssignment): SqlDbKey {
                    return assignment.burstKey as SqlDbKey
                }
            }
        private val logger = LoggerFactory.getLogger(SqlAccountStore::class.java)
        private val accountAssetDbKeyFactory =
            object : SqlDbKey.LinkKeyFactory<Account.AccountAsset>("account_id", "asset_id") {
                override fun newKey(accountAsset: Account.AccountAsset): SqlDbKey {
                    return accountAsset.burstKey as SqlDbKey
                }
            }

        private fun getAccountsWithRewardRecipientClause(id: Long, height: Int): Condition {
            return REWARD_RECIP_ASSIGN.RECIP_ID.eq(id).and(REWARD_RECIP_ASSIGN.FROM_HEIGHT.le(height))
        }
    }
}
