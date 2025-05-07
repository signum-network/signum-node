package brs.db.sql.dialects;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.SQLDialect;



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
}
