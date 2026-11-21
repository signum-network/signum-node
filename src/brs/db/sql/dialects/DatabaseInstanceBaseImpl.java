package brs.db.sql.dialects;

import brs.props.PropertyService;
import brs.props.Props;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.OptionalLong;


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

  /** @return the query that returns the database size in bytes as its only value, or null if there is none */
  protected String getDatabaseSizeSQL() {
    return null;
  }

  @Override
  public OptionalLong getDatabaseSizeBytes() {
    String sql = getDatabaseSizeSQL();
    if (sql == null) {
      return OptionalLong.empty();
    }
    try (Connection connection = getDataSource().getConnection();
         PreparedStatement statement = connection.prepareStatement(sql);
         ResultSet resultSet = statement.executeQuery()) {
      if (!resultSet.next()) {
        return OptionalLong.empty();
      }
      long size = resultSet.getLong(1);
      return resultSet.wasNull() ? OptionalLong.empty() : OptionalLong.of(size);
    } catch (SQLException | RuntimeException e) {
      logger.debug("Could not determine the database size", e);
      return OptionalLong.empty();
    }
  }

  @Override
  public Optional<Path> getDatabaseFile() {
    return Optional.empty();
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
