package brs.db.sql;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DbTest {

    @Test
    void testRollbackAndClear_GivenRollbackSucceeds_RollsBackAndClearsState() throws SQLException {
        Connection con = mock(Connection.class);
        AtomicBoolean cleared = new AtomicBoolean();

        assertDoesNotThrow(() -> Db.rollbackAndClear(con, () -> cleared.set(true)));

        verify(con).rollback();
        assertTrue(cleared.get());
    }

    @Test
    void testRollbackAndClear_GivenRollbackFails_ClearsStateAndThrowsRollbackFailure() throws SQLException {
        Connection con = mock(Connection.class);
        SQLException rollbackFailure = new SQLException("connection lost");
        doThrow(rollbackFailure).when(con).rollback();
        AtomicBoolean cleared = new AtomicBoolean();

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> Db.rollbackAndClear(con, () -> cleared.set(true)));

        assertSame(rollbackFailure, thrown.getCause());
        assertTrue(cleared.get());
    }

    @Test
    void testRollbackAndClear_GivenOnlyClearFails_ThrowsClearFailure() {
        Connection con = mock(Connection.class);
        IllegalStateException clearFailure = new IllegalStateException("cache flush failed");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> Db.rollbackAndClear(con, () -> {
                    throw clearFailure;
                }));

        assertSame(clearFailure, thrown);
    }

    @Test
    void testRollbackAndClear_GivenRollbackAndClearFail_ThrowsRollbackFailureWithClearSuppressed() throws SQLException {
        Connection con = mock(Connection.class);
        SQLException rollbackFailure = new SQLException("connection lost");
        doThrow(rollbackFailure).when(con).rollback();
        IllegalStateException clearFailure = new IllegalStateException("cache flush failed");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> Db.rollbackAndClear(con, () -> {
                    throw clearFailure;
                }));

        assertSame(rollbackFailure, thrown.getCause());
        assertEquals(1, thrown.getSuppressed().length);
        assertArrayEquals(new Throwable[] {clearFailure}, thrown.getSuppressed());
    }
}
