package brs.db.sql.dialects;

import brs.props.PropertyService;
import brs.props.Props;
import com.zaxxer.hikari.HikariConfig;
import org.jooq.SQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseInstanceSqlite extends DatabaseInstanceBaseImpl {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseInstanceSqlite.class);

  protected DatabaseInstanceSqlite(PropertyService propertyService) {
    super(propertyService);
  }

  private String getJournalMode(){
    String journalMode = propertyService.getString(Props.DB_SQLITE_JOURNAL_MODE).toUpperCase();
    if(
      journalMode.equals("WAL") ||
      journalMode.equals("TRUNCATE") ||
      journalMode.equals("DELETE") ||
      journalMode.equals("PERSIST")
    ){
      return journalMode;
    }
    return "WAL";
  }

  private String getSynchronousMode(){
    String synchronous = propertyService.getString(Props.DB_SQLITE_SYNCHRONOUS).toUpperCase();
    switch (synchronous) {
      case "FULL":
        return "FULL";
      case "OFF":
        logger.warn("SQLite synchronous mode set to: OFF. This could result in a database corruption, when the operating system crashes or the computer loses power!");
        return "OFF";
      default:
        return "NORMAL";
    }
  }

  private int getCacheSize(){
    int cacheSize = propertyService.getInt(Props.DB_SQLITE_CACHE_SIZE);
    return cacheSize;
  }

  @Override
  protected HikariConfig configureImpl(HikariConfig config) {
    config.setMaximumPoolSize(10);
    config.setConnectionTestQuery("SELECT 1;");
    config.addDataSourceProperty("foreign_keys", "off");
    config.addDataSourceProperty("busy_timeout", "30000");
    config.addDataSourceProperty("wal_autocheckpoint", "500");
    config.addDataSourceProperty("journal_mode", getJournalMode());
    config.addDataSourceProperty("synchronous", getSynchronousMode());
    config.addDataSourceProperty("cache_size", getCacheSize());
    return config;
  }

  @Override
  protected void onShutdownImpl() {
    logger.info("Applying SQLite Checkpoint...");
    executeSQL("PRAGMA wal_checkpoint(TRUNCATE)");
  }

  @Override
  public SQLDialect getDialect() {
    return SQLDialect.SQLITE;
  }


  @Override
  protected void onStartupImpl() {

    if (propertyService.getBoolean(Props.DB_OPTIMIZE)) {
      logger.info("SQLite optimization started...");
      executeSQL("PRAGMA optimize");
      logger.info("SQLite VACUUM started, this can take a while");
      executeSQL("VACUUM");
    }

  }


  @Override
  public String getMigrationSqlScriptPath() {
    return "classpath:/db/migration_sqlite";
  }

  @Override
  public String getDatabaseVersionSQLScript() {
    return "SELECT sqlite_version()";
  }

  @Override
  public boolean isStable() {
    return false;
  }
}
