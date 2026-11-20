package brs.db.sql.dialects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import brs.props.CaselessProperties;
import brs.props.PropertyServiceImpl;
import brs.props.Props;

class DatabaseInstanceSqliteTest {

    private static final String WAL_AUTOCHECKPOINT = "DB.SqliteWalAutocheckpoint";
    private static final String CACHE_SIZE = "DB.SqliteCacheSize";

    @TempDir
    Path dir;

    private DatabaseInstanceSqlite instance;

    @AfterEach
    void tearDown() {
        if (instance != null) {
            instance.getDataSource().close();
        }
    }

    @Test
    void walAutocheckpoint_GivenNoSetting_Uses10000Pages() throws SQLException {
        instance = open(null, null);

        assertEquals(10_000, pragma("wal_autocheckpoint"));
    }

    @Test
    void walAutocheckpoint_GivenSetting_UsesIt() throws SQLException {
        instance = open(WAL_AUTOCHECKPOINT, "2000");

        assertEquals(2000, pragma("wal_autocheckpoint"));
    }

    @Test
    void walAutocheckpoint_GivenZero_UsesTheDefaultInsteadOfDisablingCheckpoints() throws SQLException {
        instance = open(WAL_AUTOCHECKPOINT, "0");

        assertEquals(10_000, pragma("wal_autocheckpoint"));
    }

    @Test
    void walAutocheckpoint_GivenNegative_UsesTheDefault() throws SQLException {
        instance = open(WAL_AUTOCHECKPOINT, "-5");

        assertEquals(10_000, pragma("wal_autocheckpoint"));
    }

    @Test
    void cacheSize_GivenNoSetting_Uses32Megabytes() throws SQLException {
        instance = open(null, null);

        assertEquals(-32_768, pragma("cache_size"));
    }

    @Test
    void cacheSize_GivenSetting_UsesIt() throws SQLException {
        instance = open(CACHE_SIZE, "-65536");

        assertEquals(-65_536, pragma("cache_size"));
    }

    private DatabaseInstanceSqlite open(String setting, String value) {
        CaselessProperties properties = new CaselessProperties();
        properties.setProperty(Props.DB_URL.getName(), "jdbc:sqlite:file:" + dir.resolve("test.sqlite.db"));
        properties.setProperty(Props.DB_OPTIMIZE.getName(), "false");
        if (setting != null) {
            properties.setProperty(setting, value);
        }
        return new DatabaseInstanceSqlite(new PropertyServiceImpl(properties));
    }

    /** The value a pooled connection was opened with. */
    private int pragma(String name) throws SQLException {
        try (Connection connection = instance.getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            result.next();
            return result.getInt(1);
        }
    }
}
