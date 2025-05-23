package brs.db.sql.dialects;

import brs.props.PropertyService;
import brs.props.Props;
import brs.schema.Tables;
import org.jooq.Table;
import brs.db.sql.Db;
import java.lang.reflect.Field;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;


public abstract class DatabaseInstanceBaseImpl implements DatabaseInstance {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseInstanceBaseImpl.class);

  private final HikariDataSource dataSource;
  private HikariConfig config = new HikariConfig();
  protected PropertyService propertyService;

  protected DatabaseInstanceBaseImpl(PropertyService propertyService){
    this.propertyService = propertyService;
    String dbUrl = propertyService.getString(Props.DB_URL);
    String dbUsername = propertyService.getString(Props.DB_USERNAME);
    String dbPassword = propertyService.getString(Props.DB_PASSWORD);
    logger.debug("Database jdbc url set to: {}", dbUrl);
    config.setJdbcUrl(dbUrl);
    config.setAutoCommit(true);
    if (dbUsername != null) {
      config.setUsername(dbUsername);
    }
    if (dbPassword != null) {
      config.setPassword(dbPassword);
    }
    config.setMaximumPoolSize(propertyService.getInt(Props.DB_CONNECTIONS));
    config = this.configureImpl(config);
    dataSource = new HikariDataSource(config);
  }

  protected abstract HikariConfig configureImpl(HikariConfig config);
  protected abstract void onShutdownImpl();
  protected abstract void onStartupImpl();

  protected void executeSQL(String sql){
    try {
      Connection c = dataSource.getConnection();
      Statement stmt = c.createStatement();
      stmt.execute(sql);
    } catch (SQLException e) {
      logger.error(e.toString(), e);
    }
  }

  /**
   * Iterate over all jOOQ tables and invoke Db.optimizeTable.
   * Only executed when {@code Props.DB_OPTIMIZE} is enabled.
   */
  protected void optimizeAllTables() {
    if (!propertyService.getBoolean(Props.DB_OPTIMIZE)) {
      return;
    }
    logger.info("Optimizing database tables ...");
    for (Field field : Tables.class.getFields()) {
      if (Table.class.isAssignableFrom(field.getType())) {
        try {
          Table<?> t = (Table<?>) field.get(null);
          Db.optimizeTable(t.getName());
        } catch (Exception e) {
          logger.debug("Failed to optimize table {}", field.getName(), e);
        }
      }
    }
  }

  @Override
  public void onShutdown() {
    if (dataSource == null || dataSource.isClosed()) {
      return;
    }
    this.onShutdownImpl();
    if (!dataSource.isClosed()) {
      logger.info("Closing Database connections...");
      dataSource.close();
    }
  }

  @Override
  public void onStartup() {
    if(dataSource != null){
      this.onStartupImpl();
    }
  }

  @Override
  public HikariConfig getConfig() {
    return this.config;
  }
  @Override
  public HikariDataSource getDataSource() {
    return this.dataSource;
  }

  @Override
  public String getMigrationClassPath() {
    return "classpath:/brs/db/sql/migration";
  }
}
