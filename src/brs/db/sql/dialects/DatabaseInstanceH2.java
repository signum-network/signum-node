package brs.db.sql.dialects;

import brs.Signum;
import brs.props.PropertyService;
import brs.props.Props;
import com.zaxxer.hikari.HikariConfig;
import org.jooq.SQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseInstanceH2 extends DatabaseInstanceBaseImpl {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseInstanceH2.class);

  protected DatabaseInstanceH2(PropertyService propertyService) {
    super(propertyService);
  }

  @Override
  protected HikariConfig configureImpl(HikariConfig config) {
    try {
      Class.forName("org.h2.Driver");
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    config.setAutoCommit(true);
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    config.addDataSourceProperty("DATABASE_TO_UPPER", "false");
    config.addDataSourceProperty("CASE_INSENSITIVE_IDENTIFIERS", "true");
    return config;
  }

  @Override
  protected void onShutdownImpl() {
    if (Signum.getPropertyService().getBoolean(Props.DB_OPTIMIZE)) {
      logger.info("H2 defragmentation started, this can take a while");
      executeSQL("SHUTDOWN DEFRAG");
    } else {
      executeSQL("SHUTDOWN");
    }
    logger.info("Database shutdown completed.");
  }

  @Override
  protected void onStartupImpl() {
    logger.warn("### DEPRECATION NOTICE ###");
    logger.warn("H2 shows certain instabilities and is not recommended for use in production, i.e. public nodes anymore.");
    logger.warn("We recommend to use SQLite as a stable alternative");
    logger.warn("--------------------------");
    logger.warn("### H2 SUPPORT WILL BE REMOVED IN VERSION 3.9 ###");
    logger.warn("--------------------------");
  }

  @Override
  public SQLDialect getDialect() {
    return SQLDialect.H2;
  }

  @Override
  public String getMigrationSqlScriptPath() {
    return "classpath:/db/migration_h2_v2";
  }

  @Override
  public String getDatabaseVersionSQLScript() {
    return "SELECT H2VERSION()";
  }

  @Override
  public SupportStatus getSupportStatus() {
    return SupportStatus.DEPRECATED;
  }
}
