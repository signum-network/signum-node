package brs.db.sql

import brs.db.*
import brs.entity.DependencyProvider
import brs.objects.Props
import brs.schema.Tables
import brs.util.logging.safeDebug
import brs.util.logging.safeInfo
import brs.util.logging.safeWarn
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.Table
import org.jooq.conf.Settings
import org.jooq.conf.StatementType
import org.jooq.impl.DSL
import org.jooq.impl.TableImpl
import org.jooq.tools.jdbc.JDBCUtils
import org.mariadb.jdbc.MariaDbDataSource
import org.mariadb.jdbc.UrlParser
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLNonTransientConnectionException
import java.util.*

internal class SqlDb(private val dp: DependencyProvider) : Db {
    private val settings = Settings()
    private val staticStatementSettings = Settings()
    private val cp: HikariDataSource
    private val dialect: SQLDialect
    private val localConnection = ThreadLocal<Connection>()
    private val transactionCaches = ThreadLocal<MutableMap<Table<*>, MutableMap<BurstKey, *>>>()
    private val transactionBatches = ThreadLocal<MutableMap<Table<*>, MutableMap<BurstKey, *>>>()

    override fun getDslContext(): DSLContext {
        val con = localConnection.get()
        val ctx = if (con == null) {
            DSL.using(cp, dialect, settings)
        } else {
            DSL.using(con, dialect, staticStatementSettings)
        }
        return ctx
    }

    override fun isInTransaction() = localConnection.get() != null

    init {
        settings.isRenderSchema = false
        staticStatementSettings.isRenderSchema = false
        staticStatementSettings.statementType = StatementType.STATIC_STATEMENT

        val dbUrl: String
        val dbUsername: String?
        val dbPassword: String?

        if (dp.propertyService.get(Props.DEV_TESTNET)) {
            dbUrl = dp.propertyService.get(Props.DEV_DB_URL)
            dbUsername = dp.propertyService.get(Props.DEV_DB_USERNAME)
            dbPassword = dp.propertyService.get(Props.DEV_DB_PASSWORD)
        } else {
            dbUrl = dp.propertyService.get(Props.DB_URL)
            dbUsername = dp.propertyService.get(Props.DB_USERNAME)
            dbPassword = dp.propertyService.get(Props.DB_PASSWORD)
        }
        dialect = JDBCUtils.dialect(dbUrl)

        logger.safeDebug { "Database jdbc url set to: $dbUrl" }
        val config = HikariConfig()
        config.jdbcUrl = dbUrl
        if (dbUsername.isNotEmpty()) config.username = dbUsername
        if (dbPassword.isNotEmpty()) config.password = dbPassword
        config.maximumPoolSize = dp.propertyService.get(Props.DB_CONNECTIONS)

        val flywayBuilder = Flyway.configure()
            .dataSource(dbUrl, dbUsername, dbPassword)
            .mixed(true)
            .baselineOnMigrate(true)
        var runFlyway = false

        when (dialect) {
            SQLDialect.MYSQL, SQLDialect.MARIADB -> {
                flywayBuilder.locations("classpath:/db/migration_mariadb")
                runFlyway = true
                config.isAutoCommit = true
                config.addDataSourceProperty("cachePrepStmts", "true")
                config.addDataSourceProperty("prepStmtCacheSize", "512")
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "4096")
                config.addDataSourceProperty("characterEncoding", "utf8mb4")
                config.addDataSourceProperty("cacheServerConfiguration", "true")
                config.addDataSourceProperty("useLocalSessionState", "true")
                config.addDataSourceProperty("useLocalTransactionState", "true")
                config.addDataSourceProperty("useUnicode", "true")
                config.addDataSourceProperty("useServerPrepStmts", "true")
                config.addDataSourceProperty("rewriteBatchedStatements", "true")
                config.addDataSourceProperty("maintainTimeStats", "false")
                config.addDataSourceProperty("useUnbufferedIO", "false")
                config.addDataSourceProperty("useReadAheadInput", "false")
                val flywayDataSource = object : MariaDbDataSource(dbUrl) {
                    override fun initialize() {
                        super.initialize()
                        val props = Properties()
                        props.setProperty("user", dbUsername)
                        props.setProperty("password", dbPassword)
                        props.setProperty("useMysqlMetadata", "true")
                        val f = MariaDbDataSource::class.java.getDeclaredField("urlParser")
                        f.isAccessible = true
                        f.set(this, UrlParser.parse(dbUrl, props))
                    }
                }
                flywayBuilder.dataSource(flywayDataSource) // TODO Remove this hack once we can use Flyway 6
                config.connectionInitSql = "SET NAMES utf8mb4;"
            }
            SQLDialect.H2 -> {
                Class.forName("org.h2.Driver")
                flywayBuilder.locations("classpath:/db/migration_h2")
                runFlyway = true
                config.isAutoCommit = true
                config.addDataSourceProperty("cachePrepStmts", "true")
                config.addDataSourceProperty("prepStmtCacheSize", "512")
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "4096")
                config.addDataSourceProperty("DATABASE_TO_UPPER", "false")
                config.addDataSourceProperty("CASE_INSENSITIVE_IDENTIFIERS", "true")
            }
            SQLDialect.SQLITE -> {
                Class.forName("org.sqlite.JDBC")
                flywayBuilder.locations("classpath:/db/migration_sqlite")
                runFlyway = true
                config.isAutoCommit = true
                config.addDataSourceProperty("journal_mode", "WAL")
                config.addDataSourceProperty("foreign_keys", "ON")
            }
            else -> logger.safeWarn { "You are using database type $dialect, which is not explicitly supported! You will need to add the driver to the classpath and even then it might not work. Supported databases are: MySQL/MariaDB, H2, SQLite" }
        }

        cp = HikariDataSource(config)

        if (runFlyway) {
            logger.safeInfo { "Running flyway migration" }
            val flyway = flywayBuilder.load()
            flyway.migrate()
        }
    }

    override fun shutdown() {
        if (dialect == SQLDialect.H2) {
            try {
                cp.connection.use { con ->
                    con.createStatement().use { stmt ->
                        // COMPACT is not giving good result.
                        if (dp.propertyService.get(Props.DB_H2_DEFRAG_ON_SHUTDOWN)) {
                            stmt.execute("SHUTDOWN DEFRAG")
                        } else {
                            stmt.execute("SHUTDOWN")
                        }
                    }
                }
            } catch (ignored: SQLNonTransientConnectionException) {
            } catch (e: SQLException) {
                logger.safeInfo(e) { null }
            } finally {
                logger.safeInfo { "Database shutdown completed." }
            }
        }
        if (!cp.isClosed) {
            cp.close()
        }
    }

    override fun <V> getCache(table: Table<*>): MutableMap<BurstKey, V> {
        assertInTransaction()
        @Suppress("UNCHECKED_CAST")
        return transactionCaches.get().computeIfAbsent(table) { mutableMapOf<BurstKey, V>() } as MutableMap<BurstKey, V>
    }

    override fun <V> getBatch(table: Table<*>): MutableMap<BurstKey, V> {
        assertInTransaction()
        @Suppress("UNCHECKED_CAST")
        return transactionBatches.get().computeIfAbsent(table) { mutableMapOf<BurstKey, V>() } as MutableMap<BurstKey, V>
    }

    override fun beginTransaction() {
        check(!isInTransaction()) { "Transaction already in progress" }
        val con = cp.connection
        con.autoCommit = false

        localConnection.set(con)
        transactionCaches.set(mutableMapOf())
        transactionBatches.set(mutableMapOf())
    }

    private fun getLocalConnection() = localConnection.get() ?: error("Not in transaction")

    override fun commitTransaction() {
        getLocalConnection().commit()
    }

    override fun rollbackTransaction() {
        getLocalConnection().rollback()
        transactionCaches.get().clear()
        transactionBatches.get().clear()
        dp.dbCacheService.flushCache()
    }

    override fun endTransaction() {
        assertInTransaction()
        try {
            getLocalConnection().close()
        } catch (ignored: Exception) {
            // Do nothing
        }
        localConnection.remove()
        transactionCaches.get().clear()
        transactionCaches.remove()
        transactionBatches.get().clear()
        transactionBatches.remove()
    }

    override fun optimizeTable(tableName: String) {
        useDslContext { ctx ->
            try {
                when (ctx.dialect()) {
                    SQLDialect.MYSQL, SQLDialect.MARIADB -> {
                        // All MySQL tables are InnoDB.
                        ctx.execute("OPTIMIZE NO_WRITE_TO_BINLOG TABLE $tableName")
                    }
                    else -> {
                    }
                }
            } catch (e: Exception) {
                logger.safeDebug(e) { "Failed to optimize table $tableName" }
            }
        }
    }

    override fun optimizeDatabase() {
        useDslContext { ctx ->
            try {
                when (ctx.dialect()) {
                    SQLDialect.SQLITE -> {
                        ctx.execute("VACUUM")
                        ctx.execute("ANALYZE")
                    }
                    else -> {
                    }
                }
            } catch (e: Exception) {
                logger.safeDebug(e) { "Failed to optimize database" }
            }
        }
    }

    override fun deleteAll() {
        if (!dp.db.isInTransaction()) {
            dp.db.transaction {
                deleteAll()
            }
            return
        }
        logger.safeWarn { "Deleting blockchain..." }
        dp.db.useDslContext { ctx ->
            // TODO use allTables list
            val tables = listOf<TableImpl<*>>(
                Tables.ACCOUNT,
                Tables.ACCOUNT_ASSET, Tables.ALIAS, Tables.ALIAS_OFFER,
                Tables.ASK_ORDER, Tables.ASSET, Tables.ASSET_TRANSFER,
                Tables.AT, Tables.AT_STATE, Tables.BID_ORDER,
                Tables.BLOCK, Tables.ESCROW, Tables.ESCROW_DECISION,
                Tables.GOODS, Tables.PEER, Tables.PURCHASE,
                Tables.PURCHASE_FEEDBACK, Tables.PURCHASE_PUBLIC_FEEDBACK,
                Tables.REWARD_RECIP_ASSIGN, Tables.SUBSCRIPTION,
                Tables.TRADE, Tables.TRANSACTION,
                Tables.UNCONFIRMED_TRANSACTION
            )
            for (table in tables) {
                ctx.truncate(table).execute()
            }
        }
    }

    override val blockDb = SqlBlockDb(dp)
    override val peerDb = SqlPeerDb(dp)
    override val transactionDb = SqlTransactionDb(dp)
    override val accountStore = SqlAccountStore(dp)
    override val aliasStore = SqlAliasStore(dp)
    override val assetStore = SqlAssetStore(dp)
    override val assetTransferStore = SqlAssetTransferStore(dp)
    override val atStore = SqlATStore(dp)
    override val blockchainStore = SqlBlockchainStore(dp)
    override val digitalGoodsStoreStore = SqlDigitalGoodsStoreStore(dp)
    override val escrowStore = SqlEscrowStore(dp)
    override val indirectIncomingStore = SqlIndirectIncomingStore(dp)
    override val orderStore = SqlOrderStore(dp)
    override val subscriptionStore = SqlSubscriptionStore(dp)
    override val tradeStore = SqlTradeStore(dp)

    override val allTables = listOf(
        blockDb,
        peerDb,
        transactionDb,
        accountStore.accountTable,
        accountStore.accountAssetTable,
        accountStore.rewardRecipientAssignmentTable,
        aliasStore.aliasTable,
        aliasStore.offerTable,
        assetStore.assetTable,
        assetTransferStore.assetTransferTable,
        atStore.atTable,
        atStore.atStateTable,
        digitalGoodsStoreStore.feedbackTable,
        digitalGoodsStoreStore.goodsTable,
        digitalGoodsStoreStore.publicFeedbackTable,
        digitalGoodsStoreStore.purchaseTable,
        escrowStore.escrowTable,
        escrowStore.decisionTable,
        indirectIncomingStore.indirectIncomingTable,
        orderStore.askOrderTable,
        orderStore.bidOrderTable,
        subscriptionStore.subscriptionTable,
        tradeStore.tradeTable
    )

    companion object {
        private val logger = LoggerFactory.getLogger(SqlDb::class.java)
    }
}
