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
        instance = open(null);

        assertEquals(10_000, walAutocheckpoint());
    }

    @Test
    void walAutocheckpoint_GivenSetting_UsesIt() throws SQLException {
        instance = open("2000");

        assertEquals(2000, walAutocheckpoint());
    }

    @Test
    void walAutocheckpoint_GivenZero_UsesTheDefaultInsteadOfDisablingCheckpoints() throws SQLException {
        instance = open("0");

        assertEquals(10_000, walAutocheckpoint());
    }

    @Test
    void walAutocheckpoint_GivenNegative_UsesTheDefault() throws SQLException {
        instance = open("-5");

        assertEquals(10_000, walAutocheckpoint());
    }

    private DatabaseInstanceSqlite open(String walAutocheckpoint) {
        CaselessProperties properties = new CaselessProperties();
        properties.setProperty(Props.DB_URL.getName(), "jdbc:sqlite:file:" + dir.resolve("test.sqlite.db"));
        properties.setProperty(Props.DB_OPTIMIZE.getName(), "false");
        if (walAutocheckpoint != null) {
            properties.setProperty("DB.SqliteWalAutocheckpoint", walAutocheckpoint);
        }
        return new DatabaseInstanceSqlite(new PropertyServiceImpl(properties));
    }

    /** The value every pooled connection was opened with. */
    private int walAutocheckpoint() throws SQLException {
        try (Connection connection = instance.getDataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA wal_autocheckpoint")) {
            result.next();
            return result.getInt(1);
        }
    }
}
