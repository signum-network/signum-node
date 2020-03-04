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
        rewardRecipientAssignmentTable = object : SqlVersionedEntityTable<Account.RewardRecipientAssignment>(
            REWARD_RECIP_ASSIGN,
            REWARD_RECIP_ASSIGN.HEIGHT,
            REWARD_RECIP_ASSIGN.LATEST,
            rewardRecipientAssignmentDbKeyFactory,
            dp
        ) {
            override fun load(ctx: DSLContext, record: Record): Account.RewardRecipientAssignment {
                return sqlToRewardRecipientAssignment(record)
            }

            override fun save(ctx: DSLContext, entity: Account.RewardRecipientAssignment) {
                val record = RewardRecipAssignRecord()
                record.accountId = entity.accountId
                record.prevRecipId = entity.prevRecipientId
                record.recipId = entity.recipientId
                record.fromHeight = entity.fromHeight
                record.height = dp.blockchainService.height
                record.latest = true
                ctx.upsert(record, REWARD_RECIP_ASSIGN.ACCOUNT_ID, REWARD_RECIP_ASSIGN.HEIGHT)
                    .execute()
            }
        }

        accountAssetTable = object : SqlVersionedEntityTable<Account.AccountAsset>(
            ACCOUNT_ASSET,
            ACCOUNT_ASSET.HEIGHT,
            ACCOUNT_ASSET.LATEST,
            accountAssetDbKeyFactory,
            dp
        ) {
            override val defaultSort = listOf<SortField<*>>(
                table.field("quantity", Long::class.java).desc(),
                table.field("account_id", Long::class.java).asc(),
                table.field("asset_id", Long::class.java).asc()
            )

            override fun load(ctx: DSLContext, record: Record): Account.AccountAsset {
                return SQLAccountAsset(record)
            }

            override fun save(ctx: DSLContext, entity: Account.AccountAsset) {
                val record = AccountAssetRecord()
                record.accountId = entity.accountId
                record.assetId = entity.assetId
                record.quantity = entity.quantity
                record.unconfirmedQuantity = entity.unconfirmedQuantity
                record.height = dp.blockchainService.height
                record.latest = true
                ctx.upsert(record, ACCOUNT_ASSET.ACCOUNT_ID, ACCOUNT_ASSET.ASSET_ID, ACCOUNT_ASSET.HEIGHT)
                    .execute()
            }
        }

        accountTable = object :
            SqlVersionedBatchEntityTable<Account>(ACCOUNT, ACCOUNT.HEIGHT, ACCOUNT.LATEST, accountDbKeyFactory, Account::class.java, dp) {
            override fun load(ctx: DSLContext, record: Record): Account {
                return sqlToAccount(record)
            }

            override fun bulkUpsert(ctx: DSLContext, entities: Collection<Account>) {
                val height = dp.blockchainService.height
                ctx.batch(entities.map { account ->
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
        return dp.db.useDslContext<Int> { ctx ->
            ctx.selectCount().from(ACCOUNT_ASSET).where(ACCOUNT_ASSET.ASSET_ID.eq(assetId))
                .and(ACCOUNT_ASSET.LATEST.isTrue).fetchOne(0, Int::class.javaPrimitiveType)!!
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

    private fun sqlToAccount(record: Record): Account {
        val account = Account(
            record.get(ACCOUNT.ID),
            accountDbKeyFactory.newKey(record.get(ACCOUNT.ID)),
            record.get(ACCOUNT.CREATION_HEIGHT))
        account.publicKey = record.get(ACCOUNT.PUBLIC_KEY)
        account.keyHeight = record.get(ACCOUNT.KEY_HEIGHT)
        account.balancePlanck = record.get(ACCOUNT.BALANCE)
        account.unconfirmedBalancePlanck = record.get(ACCOUNT.UNCONFIRMED_BALANCE)
        account.forgedBalancePlanck = record.get(ACCOUNT.FORGED_BALANCE)
        account.name = record.get(ACCOUNT.NAME)
        account.description = record.get(ACCOUNT.DESCRIPTION)
        return account
    }

    private fun sqlToRewardRecipientAssignment(record: Record) = Account.RewardRecipientAssignment(
        record.get(REWARD_RECIP_ASSIGN.ACCOUNT_ID),
        record.get(REWARD_RECIP_ASSIGN.PREV_RECIP_ID),
        record.get(REWARD_RECIP_ASSIGN.RECIP_ID),
        record.get(REWARD_RECIP_ASSIGN.FROM_HEIGHT),
        rewardRecipientAssignmentDbKeyFactory.newKey(record.get(REWARD_RECIP_ASSIGN.ACCOUNT_ID)))

    companion object {
        private val logger = LoggerFactory.getLogger(SqlAccountStore::class.java)

        private val accountDbKeyFactory = object : SqlDbKey.LongKeyFactory<Account>(ACCOUNT.ID) {
            override fun newKey(entity: Account): SqlDbKey {
                return entity.nxtKey as SqlDbKey
            }
        }
        private val rewardRecipientAssignmentDbKeyFactory =
            object : SqlDbKey.LongKeyFactory<Account.RewardRecipientAssignment>(REWARD_RECIP_ASSIGN.ACCOUNT_ID) {
                override fun newKey(entity: Account.RewardRecipientAssignment): SqlDbKey {
                    return entity.burstKey as SqlDbKey
                }
            }
        private val accountAssetDbKeyFactory =
            object : SqlDbKey.LinkKeyFactory<Account.AccountAsset>("account_id", "asset_id") {
                override fun newKey(entity: Account.AccountAsset): SqlDbKey {
                    return entity.burstKey as SqlDbKey
                }
            }

        private fun getAccountsWithRewardRecipientClause(id: Long, height: Int): Condition {
            return REWARD_RECIP_ASSIGN.RECIP_ID.eq(id).and(REWARD_RECIP_ASSIGN.FROM_HEIGHT.le(height))
        }
    }
}
