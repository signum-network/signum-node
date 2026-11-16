package brs.db.sql.dialects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class DatabaseInstancePostgresTest {

    @Test
    void getSupportStatus_GivenPostgres_IsStable() {
        // Arrange: skip the constructor, which would connect to a server
        DatabaseInstancePostgres instance = mock(DatabaseInstancePostgres.class, CALLS_REAL_METHODS);

        // Act
        DatabaseInstance.SupportStatus status = instance.getSupportStatus();

        // Assert
        assertEquals(DatabaseInstance.SupportStatus.STABLE, status);
    }
}
