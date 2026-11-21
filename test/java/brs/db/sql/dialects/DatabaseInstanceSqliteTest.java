package brs.db.sql.dialects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
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

    @Test
    void sqliteFileSize_GivenDbAndWalFile_SumsBoth() throws IOException {
        Path db = dir.resolve("x.db");
        Files.write(db, new byte[100]);
        Files.write(dir.resolve("x.db-wal"), new byte[20]);

        assertEquals(OptionalLong.of(120), DatabaseInstanceSqlite.sqliteFileSize(db));
    }

    @Test
    void sqliteFileSize_GivenOnlyDbFile_ReturnsItsSize() throws IOException {
        Path db = dir.resolve("x.db");
        Files.write(db, new byte[100]);

        assertEquals(OptionalLong.of(100), DatabaseInstanceSqlite.sqliteFileSize(db));
    }

    @Test
    void sqliteFileSize_GivenMissingFile_IsEmpty() {
        assertTrue(DatabaseInstanceSqlite.sqliteFileSize(dir.resolve("missing.db")).isEmpty());
    }

    @Test
    void sqliteFile_GivenUrlWithParameters_IgnoresThem() {
        assertEquals(Optional.of(dir.resolve("x.db").toAbsolutePath()),
                DatabaseInstanceSqlite.sqliteFile("jdbc:sqlite:file:" + dir.resolve("x.db") + "?journal_mode=WAL"));
    }

    @Test
    void sqliteFile_GivenUrlWithoutFilePrefix_ReturnsAbsolutePath() {
        assertEquals(Optional.of(Path.of("db/x.db").toAbsolutePath()), DatabaseInstanceSqlite.sqliteFile("jdbc:sqlite:db/x.db"));
        assertTrue(DatabaseInstanceSqlite.sqliteFile("jdbc:mariadb://localhost/signum").isEmpty());
    }

    @Test
    void getDatabaseFile_GivenInstance_ReturnsTheDbFile() {
        instance = open(null, null);

        assertEquals(Optional.of(dir.resolve("test.sqlite.db").toAbsolutePath()), instance.getDatabaseFile());
        assertTrue(instance.getDatabaseSizeBytes().orElse(0) > 0);
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
