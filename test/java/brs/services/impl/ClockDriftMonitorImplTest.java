package brs.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import brs.services.TimeService;
import brs.util.Time.FasterTime;

class ClockDriftMonitorImplTest {

    private static final int THRESHOLD = 10;
    private static final int BLOCK_INTERVAL = 240;

    private FakeTime time;
    private RecordingLog log;
    private ClockDriftMonitorImpl monitor;

    private int height;
    private int blockTimestamp;
    private boolean referenceSent;

    @BeforeEach
    void setUp() {
        time = new FakeTime(1_000_000);
        log = new RecordingLog();
        height = 100;
        blockTimestamp = 1_000_000;
        monitor = newMonitor(THRESHOLD);
    }

    private ClockDriftMonitorImpl newMonitor(int threshold) {
        referenceSent = false;
        return new ClockDriftMonitorImpl(time, threshold, log);
    }

    @Test
    void getEstimatedOffset_GivenNoBlocks_IsEmpty() {
        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
        assertFalse(monitor.isDrifting());
    }

    @Test
    void getEstimatedOffset_GivenFewerThanMinimumSamples_IsEmpty() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES - 1, 0);

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void getEstimatedOffset_GivenTipBlocksWithClockInSync_ReportsPropagationDelayOnly() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES, 2);

        assertEquals(OptionalInt.of(2), monitor.getEstimatedOffsetSeconds());
        assertFalse(monitor.isDrifting());
    }

    @Test
    void getEstimatedOffset_GivenClockBehind_IsNegative() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES, -60);

        assertEquals(OptionalInt.of(-60), monitor.getEstimatedOffsetSeconds());
        assertTrue(monitor.isDrifting());
    }

    @Test
    void getEstimatedOffset_GivenClockAhead_IsPositive() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES, 45);

        assertEquals(OptionalInt.of(45), monitor.getEstimatedOffsetSeconds());
        assertTrue(monitor.isDrifting());
    }

    @Test
    void getEstimatedOffset_GivenOneForgerWithBadClock_UsesMedian() {
        receiveTipBlocks(4, 1);
        receiveTipBlock(200); // a forger whose clock is behind: its block looks late
        receiveTipBlocks(3, 1); // the first of these is skipped: it arrived "early" after the late one

        assertEquals(OptionalInt.of(1), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void getEstimatedOffset_GivenMoreThanWindowSamples_ForgetsOldSamples() {
        receiveTipBlocks(ClockDriftMonitorImpl.WINDOW_SIZE, -60);
        receiveTipBlocks(ClockDriftMonitorImpl.WINDOW_SIZE, 1);

        assertEquals(OptionalInt.of(1), monitor.getEstimatedOffsetSeconds());
        assertFalse(monitor.isDrifting());
    }

    @Test
    void onBlockReceived_GivenBulkSyncBlocks_TakesNoSamples() {
        // during sync, old blocks arrive back to back, far faster than their timestamps advance
        for (int i = 0; i < 50; i++) {
            height++;
            blockTimestamp += BLOCK_INTERVAL;
            monitor.onBlockReceived(height, blockTimestamp);
        }

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockReceived_GivenBulkSyncBlocksWithShortDeadlines_TakesNoSamples() {
        for (int i = 0; i < 50; i++) {
            height++;
            blockTimestamp += 5;
            time.advance(1);
            monitor.onBlockReceived(height, blockTimestamp);
        }

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockReceived_GivenSameHeightAgain_TakesNoSample() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES - 1, 0);
        time.advance(BLOCK_INTERVAL);

        monitor.onBlockReceived(height, blockTimestamp); // re-downloaded after a rejection

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockReceived_GivenHeightGap_TakesNoSampleForThatBlock() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES - 1, 0);
        height += 5;
        blockTimestamp += 5 * BLOCK_INTERVAL;
        time.advance(BLOCK_INTERVAL);

        monitor.onBlockReceived(height, blockTimestamp);

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockReceived_GivenDriftAboveThreshold_WarnsOnce() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES, -60);

        assertEquals(List.of(-60), log.warnings);
    }

    @Test
    void onBlockReceived_GivenOngoingDrift_RateLimitsWarnings() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES, -60);
        receiveTipBlocks(2, -60); // 2 * 240 s < warn interval

        assertEquals(1, log.warnings.size());

        receiveTipBlocks(1, -60); // now 720 s after the first warning

        assertEquals(2, log.warnings.size());
    }

    @Test
    void onBlockReceived_GivenClockBackInRange_LogsRecoveryOnceAndStopsWarning() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES, -60);
        receiveTipBlocks(ClockDriftMonitorImpl.WINDOW_SIZE, 1);
        int warningsUntilRecovered = log.warnings.size();

        receiveTipBlocks(ClockDriftMonitorImpl.WINDOW_SIZE, 1);

        assertEquals(List.of(1), log.recoveries);
        assertEquals(warningsUntilRecovered, log.warnings.size());
    }

    @Test
    void onBlockReceived_GivenDriftWithinThreshold_NeverWarns() {
        receiveTipBlocks(ClockDriftMonitorImpl.WINDOW_SIZE, THRESHOLD);

        assertTrue(log.warnings.isEmpty());
        assertTrue(log.recoveries.isEmpty());
    }

    @Test
    void isDrifting_GivenZeroThreshold_IsDisabled() {
        monitor = newMonitor(0);

        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES, -600);

        assertEquals(OptionalInt.of(-600), monitor.getEstimatedOffsetSeconds());
        assertFalse(monitor.isDrifting());
        assertTrue(log.warnings.isEmpty());
    }

    /** Lets {@code count} block intervals pass, each ending with a tip block seen with the given offset. */
    private void receiveTipBlocks(int count, int offset) {
        if (!referenceSent) {
            // the first block only sets the reference for the next one; it arrives with the same offset
            time.set(blockTimestamp + offset);
            monitor.onBlockReceived(height, blockTimestamp);
            referenceSent = true;
        }
        for (int i = 0; i < count; i++) {
            receiveTipBlock(offset);
        }
    }

    private void receiveTipBlock(int offset) {
        height++;
        blockTimestamp += BLOCK_INTERVAL;
        time.set(blockTimestamp + offset);
        monitor.onBlockReceived(height, blockTimestamp);
    }

    private static final class FakeTime implements TimeService {
        private int now;

        FakeTime(int now) {
            this.now = now;
        }

        void set(int now) {
            this.now = now;
        }

        void advance(int seconds) {
            now += seconds;
        }

        @Override
        public int getEpochTime() {
            return now;
        }

        @Override
        public long getEpochTimeMillis() {
            return now * 1000L;
        }

        @Override
        public void setTime(FasterTime fasterTime) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingLog implements ClockDriftMonitorImpl.DriftLog {
        final List<Integer> warnings = new ArrayList<>();
        final List<Integer> recoveries = new ArrayList<>();

        @Override
        public void warn(int offsetSeconds) {
            warnings.add(offsetSeconds);
        }

        @Override
        public void recovered(int offsetSeconds) {
            recoveries.add(offsetSeconds);
        }
    }
}
