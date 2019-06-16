package brs.db.sql;

import brs.Burst;
import brs.db.BurstKey;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.store.Dbs;
import brs.props.PropertyService;
import brs.props.Props;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;
import org.mariadb.jdbc.MariaDbDataSource;
import org.mariadb.jdbc.UrlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class Db {

  private static final Logger logger = LoggerFactory.getLogger(Db.class);

  private static HikariDataSource cp;
  private static SQLDialect dialect;
  private static final ThreadLocal<Connection> localConnection = new ThreadLocal<>();
  private static final ThreadLocal<Map<String, Map<BurstKey, Object>>> transactionCaches = new ThreadLocal<>();
  private static final ThreadLocal<Map<String, Map<BurstKey, Object>>> transactionBatches = new ThreadLocal<>();

  private static DBCacheManagerImpl dbCacheManager;

  public static void init(PropertyService propertyService, DBCacheManagerImpl dbCacheManager) {
    Db.dbCacheManager = dbCacheManager;

    String dbUrl;
    String dbUsername;
    String dbPassword;

    if (Burst.getPropertyService().getBoolean(Props.DEV_TESTNET)) {
      dbUrl = propertyService.getString(Props.DEV_DB_URL);
      dbUsername = propertyService.getString(Props.DEV_DB_USERNAME);
      dbPassword = propertyService.getString(Props.DEV_DB_PASSWORD);
    }
    else {
      dbUrl = propertyService.getString(Props.DB_URL);
      dbUsername = propertyService.getString(Props.DB_USERNAME);
      dbPassword = propertyService.getString(Props.DB_PASSWORD);
    }
    dialect = JDBCUtils.dialect(dbUrl);

    logger.debug("Database jdbc url set to: {}", dbUrl);
    try {
      HikariConfig config = new HikariConfig();
      config.setJdbcUrl(dbUrl);
      if (dbUsername != null)
        config.setUsername(dbUsername);
      if (dbPassword != null)
        config.setPassword(dbPassword);

      config.setMaximumPoolSize(propertyService.getInt(Props.DB_CONNECTIONS));

      FluentConfiguration flywayBuilder = Flyway.configure()
              .dataSource(dbUrl, dbUsername, dbPassword)
              .baselineOnMigrate(true);
      boolean runFlyway = false;

      switch (dialect) {
        case MYSQL:
        case MARIADB:
          flywayBuilder.locations("classpath:/db/migration_mariadb");
          runFlyway = true;
          config.setAutoCommit(true);
          config.addDataSourceProperty("cachePrepStmts", "true");
          config.addDataSourceProperty("prepStmtCacheSize", "250");
          config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
          config.addDataSourceProperty("characterEncoding", "utf8mb4");
          config.addDataSourceProperty("useUnicode", "true");
          config.addDataSourceProperty("useServerPrepStmts", "false");
          config.addDataSourceProperty("rewriteBatchedStatements", "true");
          MariaDbDataSource flywayDataSource = new MariaDbDataSource(dbUrl) {
            @Override
            protected synchronized void initialize() throws SQLException {
              super.initialize();
              Properties props = new Properties();
              props.setProperty("user", dbUsername);
              props.setProperty("password", dbPassword);
              props.setProperty("useMysqlMetadata", "true");
              try {
                Field f = MariaDbDataSource.class.getDeclaredField("urlParser");
                f.setAccessible(true);
                f.set(this, UrlParser.parse(dbUrl, props));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
          };
          flywayBuilder.dataSource(flywayDataSource); // TODO Remove this hack once a stable version of Flyway has this bug fixed
          config.setConnectionInitSql("SET NAMES utf8mb4;");
          break;
        case H2:
          Class.forName("org.h2.Driver");
          flywayBuilder.locations("classpath:/db/migration_h2");
          runFlyway = true;
          config.setAutoCommit(true);
          config.addDataSourceProperty("cachePrepStmts", "true");
          config.addDataSourceProperty("prepStmtCacheSize", "250");
          config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
          config.addDataSourceProperty("DATABASE_TO_UPPER", "false");
          config.addDataSourceProperty("CASE_INSENSITIVE_IDENTIFIERS", "true");
          break;
        default:
          break;
      }

      cp = new HikariDataSource(config);

      if (runFlyway) {
        logger.info("Running flyway migration");
        Flyway flyway = flywayBuilder.load();
        flyway.migrate();
      }
    } catch (Exception e) {
      throw new RuntimeException(e.toString(), e);
    }
  }

  private Db() {
  } // never

  public static Dbs getDbsByDatabaseType() {
    logger.info("Using SQL Backend with Dialect {}", dialect.getName());
    return new SqlDbs();
  }


  public static void analyzeTables() {
    if (dialect == SQLDialect.H2) {
      try (Connection con = cp.getConnection();
           Statement stmt = con.createStatement()) {
        stmt.execute("ANALYZE SAMPLE_SIZE 0");
      } catch (SQLException e) {
        throw new RuntimeException(e.toString(), e);
      }
    }
  }

  public static void shutdown() {
    if (dialect == SQLDialect.H2) {
      try ( Connection con = cp.getConnection(); Statement stmt = con.createStatement() ) {
        // COMPACT is not giving good result.
        if(Burst.getPropertyService().getBoolean(Props.DB_H2_DEFRAG_ON_SHUTDOWN)) {
          stmt.execute("SHUTDOWN DEFRAG");
        } else {
          stmt.execute("SHUTDOWN");
        }
      }
      catch (SQLException e) {
        logger.info(e.toString(), e);
      }
      finally {
        logger.info("Database shutdown completed.");
      }
    }
    if (cp != null && !cp.isClosed() ) {
      cp.close();
    }
  }

  private static Connection getPooledConnection() throws SQLException {
      return cp.getConnection();
  }

  public static Connection getConnection() throws SQLException {
    Connection con = localConnection.get();
    if (con != null) {
      return con;
    }

    con = getPooledConnection();
    con.setAutoCommit(true);

    return con;
  }

  public static DSLContext getDSLContext() {
    Connection con    = localConnection.get();
    Settings settings = new Settings();
    settings.setRenderSchema(Boolean.FALSE);

    if ( con == null ) {
      try ( DSLContext ctx = DSL.using(cp, dialect, settings) ) {
        return ctx;
      }
    }
    else {
      try ( DSLContext ctx = DSL.using(con, dialect, settings) ) {
        return ctx;
      }
    }
  }

  static <V> Map<BurstKey, V> getCache(String tableName) {
    if (!isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
    //noinspection unchecked
    return (Map<BurstKey, V>) transactionCaches.get().computeIfAbsent(tableName, k -> new HashMap<>());
  }

  static <V> Map<BurstKey, V> getBatch(String tableName) {
    if (!isInTransaction()) {
      throw new IllegalStateException("Not in transaction");
    }
    //noinspection unchecked
    return (Map<BurstKey, V>) transactionBatches.get().computeIfAbsent(tableName, k -> new HashMap<>());
  }

  public static boolean isInTransaction() {
    return localConnection.get() != null;
  }

  public static Connection beginTransaction() {
    if (localConnection.get() != null) {
      throw new IllegalStateException("Transaction already in progress");
    }
    try {
      Connection con = cp.getConnection();
      con.setAutoCommit(false);

      localConnection.set(con);
      transactionCaches.set(new HashMap<>());
      transactionBatches.set(new HashMap<>());

      return con;
    }
    catch (Exception e) {
      throw new RuntimeException(e.toString(), e);
    }
  }

  public static void commitTransaction() {
    Connection con = localConnection.get();
    if (con == null) {
      throw new IllegalStateException("Not in transaction");
    }
    try {
      con.commit();
    }
    catch (SQLException e) {
      throw new RuntimeException(e.toString(), e);
    }
  }

  public static void rollbackTransaction() {
    Connection con = localConnection.get();
    if (con == null) {
      throw new IllegalStateException("Not in transaction");
    }
    try {
      con.rollback();
    }
    catch (SQLException e) {
      throw new RuntimeException(e.toString(), e);
    }
    transactionCaches.get().clear();
    transactionBatches.get().clear();
    dbCacheManager.flushCache();
  }

  public static void endTransaction() {
    Connection con = localConnection.get();
    if (con == null) {
      throw new IllegalStateException("Not in transaction");
    }
    localConnection.set(null);
    transactionCaches.get().clear();
    transactionCaches.set(null);
    transactionBatches.get().clear();
    transactionBatches.set(null);
    DbUtils.close(con);
  }

  public static void optimizeTable(String tableName) {
    DSLContext ctx = getDSLContext();
    try {
      switch (ctx.dialect()) {
        case MYSQL:
        case MARIADB:
          ctx.execute("OPTIMIZE NO_WRITE_TO_BINLOG TABLE " + tableName);
          break;
        default:
            break;
      }
    } catch (Exception e) {
      logger.debug("Failed to optimize table {}", tableName, e);
    }
  }
}
