package brs;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockchainProcessorImplTest {

    @Test
    void isDatabaseError_givenDataAccessException_returnsTrue() {
        assertTrue(BlockchainProcessorImpl.isDatabaseError(
                new DataAccessException("Cannot add or update a child row: a foreign key constraint fails")));
    }

    @Test
    void isDatabaseError_givenSQLException_returnsTrue() {
        assertTrue(BlockchainProcessorImpl.isDatabaseError(new SQLException("connection lost")));
    }

    @Test
    void isDatabaseError_givenWrappedSQLException_returnsTrue() {
        assertTrue(BlockchainProcessorImpl.isDatabaseError(
                new RuntimeException("wrapped", new SQLException("deadlock"))));
    }

    @Test
    void isDatabaseError_givenNonDatabaseException_returnsFalse() {
        assertFalse(BlockchainProcessorImpl.isDatabaseError(new IllegalStateException("not a db error")));
    }

    @Test
    void isDatabaseError_givenNull_returnsFalse() {
        assertFalse(BlockchainProcessorImpl.isDatabaseError(null));
    }
}
