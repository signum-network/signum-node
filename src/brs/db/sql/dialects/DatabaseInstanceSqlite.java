package brs.db.sql.dialects;

import brs.props.PropertyService;
import brs.props.Props;
import com.zaxxer.hikari.HikariConfig;
import org.jooq.SQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.OptionalLong;

public class DatabaseInstanceSqlite extends DatabaseInstanceBaseImpl {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseInstanceSqlite.class);

  protected DatabaseInstanceSqlite(PropertyService propertyService) {
    super(propertyService);
  }

  private String getJournalMode() {
    String journalMode = propertyService.getString(Props.DB_SQLITE_JOURNAL_MODE).toUpperCase();
    if (
      journalMode.equals("WAL") ||
        journalMode.equals("TRUNCATE") ||
        journalMode.equals("DELETE") ||
        journalMode.equals("PERSIST")
    ) {
      return journalMode;
    }
    return "WAL";
  }

  private String getSynchronousMode() {
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

  private int getCacheSize() {
    return propertyService.getInt(Props.DB_SQLITE_CACHE_SIZE);
  }

  /**
   * WAL pages after which SQLite checkpoints the WAL into the database file. A value below 1 would turn
   * automatic checkpoints off and let the WAL grow without limit, so it falls back to the default.
   */
  private int getWalAutocheckpoint() {
    int pages = propertyService.getInt(Props.DB_SQLITE_WAL_AUTOCHECKPOINT);
    if (pages < 1) {
      int fallback = Props.DB_SQLITE_WAL_AUTOCHECKPOINT.getDefaultValue();
      logger.warn("{} = {} would disable WAL checkpoints, using {}", Props.DB_SQLITE_WAL_AUTOCHECKPOINT.getName(),
          pages, fallback);
      return fallback;
    }
    return pages;
  }

  @Override
  protected HikariConfig configureImpl(HikariConfig config) {

    ensureSqliteFolder();

    config.setMaximumPoolSize(10);
    config.setConnectionTestQuery("SELECT 1;");
    config.addDataSourceProperty("foreign_keys", "off");
    config.addDataSourceProperty("busy_timeout", "30000");
    // sqlite-jdbc reads its settings with Properties.getProperty, which ignores values that are not strings
    config.addDataSourceProperty("wal_autocheckpoint", String.valueOf(getWalAutocheckpoint()));
    config.addDataSourceProperty("journal_mode", getJournalMode());
    config.addDataSourceProperty("synchronous", getSynchronousMode());
    config.addDataSourceProperty("cache_size", String.valueOf(getCacheSize()));
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
  public Optional<Path> getDatabaseFile() {
    return sqliteFile(propertyService.getString(Props.DB_URL));
  }

  @Override
  public OptionalLong getDatabaseSizeBytes() {
    return getDatabaseFile().map(DatabaseInstanceSqlite::sqliteFileSize).orElse(OptionalLong.empty());
  }

  /** @return the absolute database file of a SQLite JDBC URL, without URL parameters */
  static Optional<Path> sqliteFile(String jdbcUrl) {
    if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlite:")) {
      return Optional.empty();
    }
    String path = jdbcUrl.startsWith("jdbc:sqlite:file:")
        ? jdbcUrl.substring("jdbc:sqlite:file:".length())
        : jdbcUrl.substring("jdbc:sqlite:".length());
    int parameters = path.indexOf('?');
    if (parameters >= 0) {
      path = path.substring(0, parameters);
    }
    return path.isEmpty() ? Optional.empty() : Optional.of(Paths.get(path).toAbsolutePath());
  }

  /** @return the size of the database file plus its write-ahead log, empty if the file is missing */
  static OptionalLong sqliteFileSize(Path dbFile) {
    try {
      long size = Files.size(dbFile);
      Path wal = dbFile.resolveSibling(dbFile.getFileName() + "-wal");
      if (Files.exists(wal)) {
        size += Files.size(wal);
      }
      return OptionalLong.of(size);
    } catch (Exception e) {
      return OptionalLong.empty();
    }
  }

  private static Path extractSqliteFolderPath(String jdbcUrl) {
    if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlite:")) {
      throw new IllegalArgumentException("Invalid SQLite JDBC URL");
    }
    String filePath = jdbcUrl.substring("jdbc:sqlite:file:".length());
    return Paths.get(filePath).toAbsolutePath().getParent();
  }

  private void ensureSqliteFolder() {
    String dbUrl = propertyService.getString(Props.DB_URL);
    Path folderPath = extractSqliteFolderPath(dbUrl);
    if (folderPath == null) {
      logger.error("SQLite database folder path couldn't be found for " + dbUrl);
      return;
    }
    if (new File(folderPath.toString()).exists()) {
      return;
    }
    logger.info("Creating SQLite DB folder(s): " + folderPath);
    try {
      Files.createDirectories(Paths.get(folderPath.toString()));
    } catch (Exception e) {
      logger.error("Failed to create SQLite DB folder: " + folderPath);
      throw new RuntimeException(e);
    }
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
  public SupportStatus getSupportStatus() {
    return SupportStatus.STABLE;
  }
}
