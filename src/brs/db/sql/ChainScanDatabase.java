package brs.db.sql;

import brs.props.PropertyService;
import brs.props.Props;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.jooq.SQLDialect;
import org.jooq.tools.jdbc.JDBCUtils;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.io.Closeable;
import java.util.Arrays;

/**
 * The read-only database connection of the chain scan. Unlike the node's {@link Db} it never runs
 * migrations or startup statements: SQLite is opened read-only, MariaDB and PostgreSQL connections
 * are marked read-only, so any write attempt fails.
 */
public final class ChainScanDatabase implements Closeable {

    private final HikariDataSource dataSource;
    private final SQLDialect dialect;

    private ChainScanDatabase(HikariDataSource dataSource, SQLDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    public static ChainScanDatabase open(PropertyService propertyService, int connections) {
        String url = propertyService.getString(Props.DB_URL);
        SQLDialect dialect = JDBCUtils.dialect(url);
        HikariConfig config = new HikariConfig();
        config.setPoolName("chain-scan");
        config.setMaximumPoolSize(connections);
        config.setAutoCommit(true);
        // Hikari sets this flag on every connection; for SQLite it must match the open mode below
        config.setReadOnly(true);
        if (dialect == SQLDialect.SQLITE) {
            // sqlite-jdbc cannot switch an open connection to read-only, so open it read-only
            SQLiteConfig sqliteConfig = new SQLiteConfig();
            sqliteConfig.setReadOnly(true);
            sqliteConfig.setBusyTimeout(30000);
            SQLiteDataSource sqlite = new SQLiteDataSource(sqliteConfig);
            sqlite.setUrl(url);
            config.setDataSource(sqlite);
        } else if (dialect == SQLDialect.MARIADB || dialect == SQLDialect.MYSQL || dialect == SQLDialect.POSTGRES) {
            config.setJdbcUrl(url);
            config.setUsername(propertyService.getString(Props.DB_USERNAME));
            config.setPassword(propertyService.getString(Props.DB_PASSWORD));
        } else {
            throw new IllegalArgumentException("Database dialect not supported: " + dialect);
        }
        return new ChainScanDatabase(new HikariDataSource(config), dialect);
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public SQLDialect getDialect() {
        return dialect;
    }

    /**
     * The migrations of this build that the database lacks. The scan reads tables as this build
     * knows them, so it refuses to run on a database that needs migrating.
     */
    public MigrationInfo[] pendingMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocations(dialect))
                .load();
        return flyway.info().pending();
    }

    /** The schema version the database is at, or {@code "none"}. */
    public String schemaVersion() {
        MigrationInfo current = Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocations(dialect))
                .load()
                .info()
                .current();
        return current == null ? "none" : current.getVersion().getVersion();
    }

    /** As the {@code getMigration*Path} methods of the {@code DatabaseInstance} dialects. */
    static String[] migrationLocations(SQLDialect dialect) {
        String sqlScripts = switch (dialect) {
            case SQLITE -> "classpath:/db/migration_sqlite";
            case POSTGRES -> "classpath:/db/migration_postgres";
            case MARIADB, MYSQL -> "classpath:/db/migration_mariadb";
            default -> throw new IllegalArgumentException("Database dialect not supported: " + dialect);
        };
        return new String[] {"classpath:/brs/db/sql/migration", sqlScripts};
    }

    @Override
    public void close() {
        dataSource.close();
    }

    @Override
    public String toString() {
        return "ChainScanDatabase{" + dialect + ", " + Arrays.toString(migrationLocations(dialect)) + "}";
    }
}
