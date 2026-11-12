package brs;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockNotPersistedExceptionTest {

    @Test
    void testConstructor_GivenPersistenceFailure_KeepsHeightFailureAndCause() {
        DataAccessException cause = new DataAccessException("foreign key constraint fails");

        BlockNotPersistedException exception = new BlockNotPersistedException(
                42, BlockPushRecovery.Failure.PERSISTENCE, cause);

        assertEquals(42, exception.getHeight());
        assertEquals(BlockPushRecovery.Failure.PERSISTENCE, exception.getFailure());
        assertSame(cause, exception.getCause());
        assertFalse(exception.isTransient());
        assertTrue(exception.getMessage().contains("foreign key constraint fails"));
    }

    @Test
    void testIsTransient_GivenTransientFailure_ReturnsTrue() {
        BlockNotPersistedException exception = new BlockNotPersistedException(
                42, BlockPushRecovery.Failure.TRANSIENT, new RuntimeException("[SQLITE_BUSY]"));

        assertTrue(exception.isTransient());
        assertTrue(BlockPushRecovery.isSqliteBusy(exception));
    }
}
