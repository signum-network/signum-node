package brs;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopOffTimerTest {

    private static final long INTERVAL = TimeUnit.SECONDS.toNanos(10);

    private final AtomicLong clock = new AtomicLong(1_000_000L);

    private PopOffTimer timer() {
        return new PopOffTimer(clock::get, INTERVAL);
    }

    private void advanceMillis(long millis) {
        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    @Test
    void format_GivenLessThanASecond_ShowsMilliseconds() {
        assertEquals("0 ms", PopOffTimer.format(0));
        assertEquals("999 ms", PopOffTimer.format(TimeUnit.MILLISECONDS.toNanos(999)));
    }

    @Test
    void format_GivenLessThanAMinute_ShowsSecondsWithOneDecimal() {
        assertEquals("1.0 s", PopOffTimer.format(TimeUnit.SECONDS.toNanos(1)));
        assertEquals("12.3 s", PopOffTimer.format(TimeUnit.MILLISECONDS.toNanos(12_345)));
    }

    @Test
    void format_GivenMinutes_ShowsMinutesAndSeconds() {
        assertEquals("1 min 00 s", PopOffTimer.format(TimeUnit.SECONDS.toNanos(60)));
        assertEquals("4 min 05 s", PopOffTimer.format(TimeUnit.SECONDS.toNanos(245)));
    }

    @Test
    void format_GivenHours_ShowsHoursAndMinutes() {
        assertEquals("2 h 03 min", PopOffTimer.format(TimeUnit.SECONDS.toNanos(2 * 3_600 + 3 * 60 + 59)));
    }

    @Test
    void format_GivenNegativeDuration_ShowsZero() {
        assertEquals("0 ms", PopOffTimer.format(-5));
    }

    @Test
    void elapsed_GivenTimeAdvanced_ShowsTheTimeSinceTheStart() {
        // Arrange
        PopOffTimer timer = timer();
        advanceMillis(2_500);

        // Act
        String elapsed = timer.elapsed();

        // Assert
        assertEquals("2.5 s", elapsed);
    }

    @Test
    void isProgressDue_GivenLessThanTheIntervalSinceTheStart_ReturnsFalse() {
        // Arrange
        PopOffTimer timer = timer();
        advanceMillis(9_999);

        // Act + Assert
        assertFalse(timer.isProgressDue());
    }

    @Test
    void isProgressDue_GivenTheIntervalPassed_ReturnsTrueOnceUntilTheNextInterval() {
        // Arrange
        PopOffTimer timer = timer();
        advanceMillis(10_000);

        // Act + Assert
        assertTrue(timer.isProgressDue());
        assertFalse(timer.isProgressDue());
        advanceMillis(9_999);
        assertFalse(timer.isProgressDue());
        advanceMillis(1);
        assertTrue(timer.isProgressDue());
    }
}
