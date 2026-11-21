package brs.db.sql.dialects;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.SQLDialect;

import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;



public interface DatabaseInstance {
  enum SupportStatus {
    STABLE,
    DEPRECATED,
    EXPERIMENTAL
  }
  void onStartup();
  void onShutdown();
  HikariConfig getConfig();
  HikariDataSource getDataSource();
  String getMigrationSqlScriptPath();
  String getMigrationClassPath();
  String getDatabaseVersionSQLScript();
  SQLDialect getDialect();
  SupportStatus getSupportStatus();

  /** @return the database's size on disk in bytes, or empty if it cannot be determined */
  OptionalLong getDatabaseSizeBytes();

  /** @return the database file for a file-based database, empty for a database server */
  Optional<Path> getDatabaseFile();
}
