package brs.db.sql

import brs.db.*
import brs.entity.DependencyProvider
import brs.objects.Props
import brs.util.logging.safeDebug
import brs.util.logging.safeInfo
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.conf.StatementType
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.JDBCUtils
import org.mariadb.jdbc.MariaDbDataSource
import org.mariadb.jdbc.UrlParser
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLNonTransientConnectionException
import java.util.*

class SqlDb(private val dp: DependencyProvider) : Db {
    private val logger = LoggerFactory.getLogger(SqlDb::class.java)

    private val settings = Settings()
    private val staticStatementSettings = Settings()
    private val cp: HikariDataSource
    private val dialect: SQLDialect
    private val localConnection = ThreadLocal<Connection>()
    private val transactionCaches = ThreadLocal<MutableMap<String, MutableMap<BurstKey, Any>>>()
    private val transactionBatches = ThreadLocal<MutableMap<String, MutableMap<BurstKey, Any>>>()

    override fun getDslContext(): DSLContext {
        val con = localConnection.get()
        val ctx = if (con == null) {
            DSL.using(cp, dialect, settings)
        } else {
            DSL.using(con, dialect, staticStatementSettings)
        }
        if (dialect == SQLDialect.SQLITE) { // TODO yuck
            ctx.execute("PRAGMA foreign_keys = ON;")
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
        try {
            val config = HikariConfig()
            config.jdbcUrl = dbUrl
            if (dbUsername.isNotEmpty()) config.username = dbUsername
            if (dbPassword.isNotEmpty()) config.password = dbPassword
            config.maximumPoolSize = dp.propertyService.get(Props.DB_CONNECTIONS)

            val flywayBuilder = Flyway.configure()
                .dataSource(dbUrl, dbUsername, dbPassword)
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
                            try {
                                val f = MariaDbDataSource::class.java.getDeclaredField("urlParser")
                                f.isAccessible = true
                                f.set(this, UrlParser.parse(dbUrl, props))
                            } catch (e: Exception) {
                                throw RuntimeException(e)
                            }
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
                }
                else -> {
                }
            }

            cp = HikariDataSource(config)

            if (runFlyway) {
                logger.safeInfo { "Running flyway migration" }
                val flyway = flywayBuilder.load()
                flyway.migrate()
            }
        } catch (e: Exception) {
            throw RuntimeException(e.toString(), e)
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

    override fun <V> getCache(tableName: String): MutableMap<BurstKey, V> {
        check(isInTransaction()) { "Not in transaction" }
        return transactionCaches.get()!!.computeIfAbsent(tableName) { mutableMapOf() } as MutableMap<BurstKey, V>
    }

    override fun <V> getBatch(tableName: String): MutableMap<BurstKey, V> {
        check(isInTransaction()) { "Not in transaction" }
        return transactionBatches.get()!!.computeIfAbsent(tableName) { mutableMapOf() } as MutableMap<BurstKey, V>
    }

    override fun beginTransaction(): Connection {
        check(!isInTransaction()) { "Transaction already in progress" }
        try {
            val con = cp.connection
            con.autoCommit = false

            localConnection.set(con)
            transactionCaches.set(mutableMapOf())
            transactionBatches.set(mutableMapOf())

            return con
        } catch (e: Exception) {
            throw RuntimeException(e.toString(), e)
        }
    }

    override fun commitTransaction() {
        val con = localConnection.get() ?: error("Not in transaction")
        try {
            con.commit()
        } catch (e: SQLException) {
            throw RuntimeException(e.toString(), e)
        }
    }

    override fun rollbackTransaction() {
        val con = localConnection.get() ?: error("Not in transaction")
        try {
            con.rollback()
        } catch (e: SQLException) {
            throw RuntimeException(e.toString(), e)
        }

        transactionCaches.get().clear()
        transactionBatches.get().clear()
        dp.dbCacheService.flushCache()
    }

    override fun endTransaction() {
        val con = localConnection.get() ?: error("Not in transaction")
        localConnection.remove()
        transactionCaches.get().clear()
        transactionCaches.remove()
        transactionBatches.get().clear()
        transactionBatches.remove()
        try {
            con.close()
        } catch (ignored: Exception) {
            // Do nothing
        }
    }

    override fun optimizeTable(tableName: String) {
        useDslContext { ctx ->
            try {
                when (ctx.dialect()) {
                    SQLDialect.MYSQL, SQLDialect.MARIADB -> ctx.execute("OPTIMIZE NO_WRITE_TO_BINLOG TABLE $tableName")
                    else -> {
                    }
                }
            } catch (e: Exception) {
                logger.safeDebug(e) { "Failed to optimize table $tableName" }
            }
        }
    }

    override fun getBlockDb(): BlockDb = SqlBlockDb(dp)
    override fun getPeerDb(): PeerDb = SqlPeerDb(dp)
    override fun getTransactionDb(): TransactionDb = SqlTransactionDb(dp)
    override fun getAccountStore(): AccountStore = SqlAccountStore(dp)
    override fun getAliasStore(): AliasStore = SqlAliasStore(dp)
    override fun getAssetStore(): AssetStore = SqlAssetStore(dp)
    override fun getAssetTransferStore(): AssetTransferStore = SqlAssetTransferStore(dp)
    override fun getATStore(): ATStore = SqlATStore(dp)
    override fun getBlockchainStore(): BlockchainStore = SqlBlockchainStore(dp)
    override fun getDigitalGoodsStoreStore(): DigitalGoodsStoreStore = SqlDigitalGoodsStoreStore(dp)
    override fun getEscrowStore(): EscrowStore = SqlEscrowStore(dp)
    override fun getIndirectIncomingStore(): IndirectIncomingStore = SqlIndirectIncomingStore(dp)
    override fun getOrderStore(): OrderStore = SqlOrderStore(dp)
    override fun getSubscriptionStore(): SubscriptionStore = SqlSubscriptionStore(dp)
    override fun getTradeStore(): TradeStore = SqlTradeStore(dp)
}
