package brs.db.sql.dialects;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseInstanceSizeQueryTest {

    /** Skips the constructors, which would connect to a server, and answers the size query with {@code size}. */
    private static <T extends DatabaseInstanceBaseImpl> ArgumentCaptor<String> answering(T instance, long size) throws SQLException {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        doReturn(dataSource).when(instance).getDataSource();
        when(dataSource.getConnection()).thenReturn(connection);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(connection.prepareStatement(sql.capture())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong(1)).thenReturn(size);
        return sql;
    }

    @Test
    void getDatabaseSizeBytes_GivenPostgres_AsksForTheCurrentDatabaseSize() throws SQLException {
        DatabaseInstancePostgres instance = mock(DatabaseInstancePostgres.class, CALLS_REAL_METHODS);
        ArgumentCaptor<String> sql = answering(instance, 12_345L);

        assertEquals(OptionalLong.of(12_345L), instance.getDatabaseSizeBytes());
        assertTrue(sql.getValue().contains("pg_database_size(current_database())"));
    }

    @Test
    void getDatabaseSizeBytes_GivenMariaDb_SumsTheCurrentSchema() throws SQLException {
        DatabaseInstanceMariaDb instance = mock(DatabaseInstanceMariaDb.class, CALLS_REAL_METHODS);
        ArgumentCaptor<String> sql = answering(instance, 67_890L);

        assertEquals(OptionalLong.of(67_890L), instance.getDatabaseSizeBytes());
        assertTrue(sql.getValue().contains("information_schema.tables"));
        assertTrue(sql.getValue().contains("DATABASE()"));
    }

    @Test
    void getDatabaseSizeBytes_GivenQueryFails_IsEmpty() throws SQLException {
        // e.g. no rights on information_schema, or the server is down
        DatabaseInstanceMariaDb instance = mock(DatabaseInstanceMariaDb.class, CALLS_REAL_METHODS);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        doReturn(dataSource).when(instance).getDataSource();
        when(dataSource.getConnection()).thenThrow(new SQLException("denied"));

        assertTrue(instance.getDatabaseSizeBytes().isEmpty());
        verify(dataSource).getConnection();
    }

    @Test
    void getDatabaseFile_GivenServerDatabase_IsEmpty() {
        assertTrue(mock(DatabaseInstancePostgres.class, CALLS_REAL_METHODS).getDatabaseFile().isEmpty());
    }
}
