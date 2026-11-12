package brs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceFailureBackoffTest {

    private static final long BASE_DELAY_MS = 500;
    private static final long MAX_DELAY_MS = 60_000;
    private static final int POP_OFF_THRESHOLD = 3;

    private final AtomicLong now = new AtomicLong(1_000_000);
    private PersistenceFailureBackoff backoff;

    @BeforeEach
    void setUp() {
        backoff = new PersistenceFailureBackoff(BASE_DELAY_MS, MAX_DELAY_MS, POP_OFF_THRESHOLD, now::get);
    }

    @Test
    void testIsRetryDue_GivenNoFailures_ReturnsTrue() {
        assertTrue(backoff.isRetryDue());
        assertEquals(0, backoff.retryDelayMs());
    }

    @Test
    void testRecordFailure_GivenConsecutiveFailures_ReturnsCount() {
        assertEquals(1, backoff.recordFailure());
        assertEquals(2, backoff.recordFailure());
        assertEquals(3, backoff.recordFailure());
    }

    @Test
    void testRetryDelayMs_GivenConsecutiveFailures_DoublesFromBaseDelay() {
        backoff.recordFailure();
        assertEquals(500, backoff.retryDelayMs());
        backoff.recordFailure();
        assertEquals(1_000, backoff.retryDelayMs());
        backoff.recordFailure();
        assertEquals(2_000, backoff.retryDelayMs());
    }

    @Test
    void testRetryDelayMs_GivenManyFailures_IsCappedAtMaxDelay() {
        for (int i = 0; i < 100; i++) {
            backoff.recordFailure();
        }

        assertEquals(MAX_DELAY_MS, backoff.retryDelayMs());
    }

    @Test
    void testIsRetryDue_GivenFailure_FalseUntilDelayElapsed() {
        backoff.recordFailure();

        assertFalse(backoff.isRetryDue());
        now.addAndGet(BASE_DELAY_MS - 1);
        assertFalse(backoff.isRetryDue());
        now.addAndGet(1);
        assertTrue(backoff.isRetryDue());
    }

    @Test
    void testIsPopOffDue_GivenFailuresBelowThreshold_ReturnsFalse() {
        backoff.recordFailure();
        backoff.recordFailure();

        assertFalse(backoff.isPopOffDue());
    }

    @Test
    void testIsPopOffDue_GivenFailuresAtAndAboveThreshold_ReturnsTrue() {
        backoff.recordFailure();
        backoff.recordFailure();
        backoff.recordFailure();
        assertTrue(backoff.isPopOffDue());

        backoff.recordFailure();
        assertTrue(backoff.isPopOffDue());
    }

    @Test
    void testRecordSuccess_GivenFailureStreak_ResetsStreak() {
        backoff.recordFailure();
        backoff.recordFailure();
        backoff.recordFailure();

        backoff.recordSuccess();

        assertTrue(backoff.isRetryDue());
        assertFalse(backoff.isPopOffDue());
        assertEquals(0, backoff.retryDelayMs());
        assertEquals(1, backoff.recordFailure());
        assertEquals(BASE_DELAY_MS, backoff.retryDelayMs());
    }

    @Test
    void testConstructor_GivenNonPositiveBaseDelay_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new PersistenceFailureBackoff(0, MAX_DELAY_MS, POP_OFF_THRESHOLD, now::get));
    }

    @Test
    void testConstructor_GivenMaxDelayBelowBaseDelay_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new PersistenceFailureBackoff(BASE_DELAY_MS, BASE_DELAY_MS - 1, POP_OFF_THRESHOLD, now::get));
    }

    @Test
    void testConstructor_GivenNonPositivePopOffThreshold_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new PersistenceFailureBackoff(BASE_DELAY_MS, MAX_DELAY_MS, 0, now::get));
    }
}
