package brs.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import brs.Constants;

class TimeTest {

    @Test
    void epochTimeGetTimeInMillis_GivenNow_IsEpochMillisWithoutRoundingOffset() {
        long before = System.currentTimeMillis() - Constants.EPOCH_BEGINNING;

        long millis = new Time.EpochTime().getTimeInMillis();

        long after = System.currentTimeMillis() - Constants.EPOCH_BEGINNING;
        assertTrue(millis >= before && millis <= after, "got " + millis + ", expected " + before + ".." + after);
    }

    @Test
    void fasterTimeGetTimeInMillis_GivenStartTime_IsInMillisAndMatchesGetTime() {
        Time.FasterTime time = new Time.FasterTime(1000, 10);

        long millis = time.getTimeInMillis();
        int seconds = time.getTime();

        assertTrue(millis >= 1_000_000L && millis < 1_000_000L + 10_000L, "got " + millis);
        assertTrue(Math.abs(millis / 1000 - seconds) <= 1, "millis " + millis + " vs seconds " + seconds);
    }
}
