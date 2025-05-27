package brs.db.sql.dialects;

import brs.props.PropertyService;
import brs.props.Props;
import com.zaxxer.hikari.HikariConfig;
import org.jooq.SQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseInstancePostgres extends DatabaseInstanceBaseImpl {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseInstancePostgres.class);

  protected DatabaseInstancePostgres(PropertyService propertyService) {
    super(propertyService);
  }

  @Override
  protected HikariConfig configureImpl(HikariConfig config) {
    // check https://jdbc.postgresql.org/documentation/use/ for more options
    return config;
  }

  @Override
  protected void onShutdownImpl() {
    if (propertyService.getBoolean(Props.DB_OPTIMIZE)) {
      logger.info("Running VACUUM ANALYZE on PostgreSQL...");
      executeSQL("VACUUM (ANALYZE)");
    }
  }

  @Override
  public SQLDialect getDialect() {
    return SQLDialect.POSTGRES;
  }

  @Override
  protected void onStartupImpl() {}

  @Override
  public String getMigrationSqlScriptPath() {
    return "classpath:/db/migration_postgres";
  }

  @Override
  public String getDatabaseVersionSQLScript() {
    return "SELECT VERSION()";
  }

  @Override
  public SupportStatus getSupportStatus() {
    return SupportStatus.EXPERIMENTAL;
  }
}
