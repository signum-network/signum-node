package brs.services.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import brs.Constants;
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
    void getEstimatedOffset_GivenBlocksPushedLateBecauseClockIsBehind_UsesFirstArrival() {
        // a clock 600 s behind rejects each block as "from the future" and pushes it ~585 s later
        for (int i = 0; i <= ClockDriftMonitorImpl.MIN_SAMPLES; i++) {
            height++;
            blockTimestamp += BLOCK_INTERVAL;
            time.set(blockTimestamp - 600);
            monitor.onBlockReceived(height);
            time.advance(585);
            monitor.onBlockPushed(height, height, blockTimestamp);
        }

        assertEquals(OptionalInt.of(-600), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void getEstimatedOffset_GivenParentPoppedAndRePushedAfterRejection_StillSamples() {
        // the importer's auto pop-off after a "from the future" rejection: the parent is popped,
        // downloaded again and pushed again before the rejected block finally gets through
        for (int i = 0; i <= ClockDriftMonitorImpl.MIN_SAMPLES; i++) {
            height++;
            blockTimestamp += BLOCK_INTERVAL;
            time.set(blockTimestamp - 60);
            monitor.onBlockReceived(height);
            time.advance(20);
            monitor.onBlockReceived(height - 1); // parent downloaded again
            monitor.onBlockPushed(height - 1, height - 1, blockTimestamp - BLOCK_INTERVAL);
            time.advance(30);
            monitor.onBlockPushed(height, height, blockTimestamp);
        }

        assertEquals(OptionalInt.of(-60), monitor.getEstimatedOffsetSeconds());
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
    void onBlockPushed_GivenBulkSyncBlocks_TakesNoSamples() {
        // during sync, old blocks arrive back to back, far faster than their timestamps advance
        for (int i = 0; i < 50; i++) {
            height++;
            blockTimestamp += BLOCK_INTERVAL;
            monitor.onBlockReceived(height);
            monitor.onBlockPushed(height, height, blockTimestamp);
        }

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockPushed_GivenBulkSyncBlocksWithShortDeadlines_TakesNoSamples() {
        for (int i = 0; i < 50; i++) {
            height++;
            blockTimestamp += 5;
            time.advance(1);
            monitor.onBlockReceived(height);
            monitor.onBlockPushed(height, height, blockTimestamp);
        }

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockPushed_GivenFakeBlockAnnouncedFirstAtEachHeight_SamplesOnlyHonestBlocks() {
        receiveTipBlocks(1, 2);
        for (int i = 0; i < ClockDriftMonitorImpl.WINDOW_SIZE; i++) {
            height++;
            blockTimestamp += BLOCK_INTERVAL;
            time.set(blockTimestamp + 2);
            monitor.onBlockReceived(-height); // a peer's fake block at this height, never pushed
            monitor.onBlockReceived(height);
            monitor.onBlockPushed(height, height, blockTimestamp);
        }

        assertEquals(OptionalInt.of(2), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockPushed_GivenBlockNeverReceived_TakesNoSampleButKeepsReference() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES - 1, 0);
        // a block this node forged itself: pushed without being received from a peer
        height++;
        blockTimestamp += BLOCK_INTERVAL;
        time.set(blockTimestamp);
        monitor.onBlockPushed(height, height, blockTimestamp);

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());

        receiveTipBlock(0);

        assertEquals(OptionalInt.of(0), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockReceived_GivenSameBlockAgain_KeepsFirstArrival() {
        for (int i = 0; i <= ClockDriftMonitorImpl.MIN_SAMPLES; i++) {
            height++;
            blockTimestamp += BLOCK_INTERVAL;
            time.set(blockTimestamp - 20);
            monitor.onBlockReceived(height);
            time.advance(120);
            monitor.onBlockReceived(height); // re-downloaded after a rejection
            monitor.onBlockPushed(height, height, blockTimestamp);
        }

        assertEquals(OptionalInt.of(-20), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockReceived_GivenFloodOfUnpushedBlocks_EvictsOldestArrivals() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES - 1, 0);
        height++;
        blockTimestamp += BLOCK_INTERVAL;
        time.set(blockTimestamp);
        monitor.onBlockReceived(height);
        for (long id = 1; id <= ClockDriftMonitorImpl.MAX_TRACKED_ARRIVALS; id++) {
            monitor.onBlockReceived(-id); // fake blocks that never get pushed
        }

        monitor.onBlockPushed(height, height, blockTimestamp);

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockReceived_GivenFullDownloadWindowAhead_KeepsArrivalOfNextBlock() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES - 1, 0);
        height++;
        blockTimestamp += BLOCK_INTERVAL;
        time.set(blockTimestamp);
        monitor.onBlockReceived(height);
        for (long id = 1; id < Constants.MAX_ROLLBACK; id++) {
            monitor.onBlockReceived(height + id); // the rest of the download window
        }

        monitor.onBlockPushed(height, height, blockTimestamp);

        assertEquals(OptionalInt.of(0), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockPushed_GivenFailingDriftLog_DoesNotThrow() {
        monitor = new ClockDriftMonitorImpl(time, THRESHOLD, new ClockDriftMonitorImpl.DriftLog() {
            @Override
            public void warn(String source, int offsetSeconds) {
                throw new IllegalStateException("logging broken");
            }

            @Override
            public void recovered(int offsetSeconds) {
                throw new IllegalStateException("logging broken");
            }
        });
        referenceSent = false;

        assertDoesNotThrow(() -> receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES, -60));
        assertEquals(OptionalInt.of(-60), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockPushed_GivenBlockFirstSeenLate_TakesNoSample() {
        // e.g. the announcement was missed and the block is first seen via download much later
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES - 1, 0);
        height++;
        blockTimestamp += BLOCK_INTERVAL;
        time.set(blockTimestamp + 360);
        monitor.onBlockReceived(height);
        monitor.onBlockPushed(height, height, blockTimestamp);

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockPushed_GivenOwnForgedBlockEchoedAndPushedAgain_TakesNoSample() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES - 1, 0);
        // the node forges a block: pushed without being received
        height++;
        blockTimestamp += BLOCK_INTERVAL;
        time.set(blockTimestamp);
        monitor.onBlockPushed(height, height, blockTimestamp);
        // a peer echoes it back, a short reorg pops it and it is pushed again
        time.advance(1);
        monitor.onBlockReceived(height);
        monitor.onBlockPushed(height - 1, height - 1, blockTimestamp - BLOCK_INTERVAL);
        monitor.onBlockPushed(height, height, blockTimestamp);

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockPushed_GivenEqualTimestamps_TakesNoSamples() {
        receiveTipBlocks(1, 0);
        for (int i = 0; i < 50; i++) {
            height++;
            time.advance(BLOCK_INTERVAL);
            monitor.onBlockReceived(height);
            monitor.onBlockPushed(height, height, blockTimestamp);
        }

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockPushed_GivenPopOff_DoesNotCountRePushedBlocksTwiceAndSamplesNewOnes() {
        receiveTipBlocks(3, 0);
        // pop off two blocks; they get pushed again (no new samples), then new blocks follow
        height -= 2;
        blockTimestamp -= 2 * BLOCK_INTERVAL;
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES + 1, 3);

        assertEquals(OptionalInt.of(3), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockPushed_GivenHeightGap_TakesNoSampleForThatBlock() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES - 1, 0);
        height += 5;
        blockTimestamp += 5 * BLOCK_INTERVAL;
        time.set(blockTimestamp);
        monitor.onBlockReceived(height);
        monitor.onBlockPushed(height, height, blockTimestamp);

        assertEquals(OptionalInt.empty(), monitor.getEstimatedOffsetSeconds());
    }

    @Test
    void onBlockPushed_GivenExtremeTimestamps_DoesNotOverflow() {
        time.set(Integer.MAX_VALUE - 10);
        monitor.onBlockReceived(1);
        monitor.onBlockPushed(1, height, Integer.MIN_VALUE);
        for (int i = 1; i <= ClockDriftMonitorImpl.MIN_SAMPLES; i++) {
            time.set(Integer.MAX_VALUE - 10 + i);
            monitor.onBlockReceived(1 + i);
            monitor.onBlockPushed(1 + i, height + i, Integer.MIN_VALUE + i);
        }

        assertTrue(monitor.getEstimatedOffsetSeconds().getAsInt() > 0);
        assertTrue(monitor.isDrifting());
    }

    @Test
    void onBlockPushed_GivenDriftAboveThreshold_WarnsOnce() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES, -60);

        assertEquals(List.of("blocks:-60"), log.warnings);
    }

    @Test
    void onBlockPushed_GivenOngoingDrift_RateLimitsWarnings() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES, -60);
        receiveTipBlocks(2, -60); // 2 * 240 s < warn interval

        assertEquals(1, log.warnings.size());

        receiveTipBlocks(1, -60); // now 720 s after the first warning

        assertEquals(2, log.warnings.size());
    }

    @Test
    void onBlockPushed_GivenClockBackInRange_LogsRecoveryOnceAndStopsWarning() {
        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES, -60);
        receiveTipBlocks(ClockDriftMonitorImpl.WINDOW_SIZE, 1);
        int warningsUntilRecovered = log.warnings.size();

        receiveTipBlocks(ClockDriftMonitorImpl.WINDOW_SIZE, 1);

        assertEquals(List.of(1), log.recoveries);
        assertEquals(warningsUntilRecovered, log.warnings.size());
    }

    @Test
    void onBlockPushed_GivenDriftWithinThreshold_NeverWarns() {
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

    @Test
    void getPeerOffset_GivenThreePeers_IsMedianInSeconds() {
        monitor.onPeerClockSample("a", 1000);
        monitor.onPeerClockSample("b", 2000);
        monitor.onPeerClockSample("c", 60000);

        assertEquals(OptionalInt.of(2), monitor.getPeerOffsetSeconds());
        assertEquals(3, monitor.getPeerSampleCount());
    }

    @Test
    void getPeerOffset_GivenNoSamples_IsEmpty() {
        assertEquals(OptionalInt.empty(), monitor.getPeerOffsetSeconds());
        assertEquals(0, monitor.getPeerSampleCount());
    }

    @Test
    void getPeerOffset_GivenTwoPeers_IsEmpty() {
        monitor.onPeerClockSample("a", 60000);
        monitor.onPeerClockSample("b", 60000);

        assertEquals(OptionalInt.empty(), monitor.getPeerOffsetSeconds());
        assertEquals(2, monitor.getPeerSampleCount());
        assertFalse(monitor.isDrifting());
    }

    @Test
    void getPeerOffset_GivenFourPeers_AveragesMiddleSamplesAndRoundsTowardZero() {
        monitor.onPeerClockSample("a", -9000);
        monitor.onPeerClockSample("b", -2500);
        monitor.onPeerClockSample("c", -1500);
        monitor.onPeerClockSample("d", 9000);

        assertEquals(OptionalInt.of(-2), monitor.getPeerOffsetSeconds());
    }

    @Test
    void onPeerClockSample_GivenSamePeerTwice_KeepsLatestOnly() {
        monitor.onPeerClockSample("a", 0);
        monitor.onPeerClockSample("b", 0);
        monitor.onPeerClockSample("c", 0);
        monitor.onPeerClockSample("a", 90000);

        assertEquals(3, monitor.getPeerSampleCount());
        assertEquals(OptionalInt.of(0), monitor.getPeerOffsetSeconds());
    }

    @Test
    void getPeerOffset_GivenExpiredSamples_IsEmpty() {
        monitor.onPeerClockSample("a", 60000);
        monitor.onPeerClockSample("b", 60000);
        monitor.onPeerClockSample("c", 60000);

        time.advance(ClockDriftMonitorImpl.PEER_SAMPLE_MAX_AGE_SECONDS + 1);

        assertEquals(OptionalInt.empty(), monitor.getPeerOffsetSeconds());
        assertEquals(0, monitor.getPeerSampleCount());
        assertFalse(monitor.isDrifting());
    }

    @Test
    void getPeerOffset_GivenSamplesAtMaxAge_KeepsThem() {
        monitor.onPeerClockSample("a", 0);
        monitor.onPeerClockSample("b", 0);
        monitor.onPeerClockSample("c", 0);

        time.advance(ClockDriftMonitorImpl.PEER_SAMPLE_MAX_AGE_SECONDS);

        assertEquals(3, monitor.getPeerSampleCount());
    }

    @Test
    void onPeerClockSample_GivenManyPeers_StaysBounded() {
        for (int i = 0; i < 300; i++) {
            monitor.onPeerClockSample("old" + i, -50000);
        }
        for (int i = 0; i < ClockDriftMonitorImpl.MAX_PEER_SAMPLES; i++) {
            monitor.onPeerClockSample("new" + i, 0);
        }

        assertEquals(OptionalInt.of(0), monitor.getPeerOffsetSeconds());
        assertEquals(ClockDriftMonitorImpl.MAX_PEER_SAMPLES, monitor.getPeerSampleCount());
    }

    @Test
    void onPeerClockSample_GivenExtremeOffsets_DoesNotOverflow() {
        monitor.onPeerClockSample("a", Long.MAX_VALUE);
        monitor.onPeerClockSample("b", Long.MAX_VALUE);
        monitor.onPeerClockSample("c", Long.MAX_VALUE);
        monitor.onPeerClockSample("d", Long.MAX_VALUE);

        assertEquals(OptionalInt.of(Integer.MAX_VALUE), monitor.getPeerOffsetSeconds());
    }

    @Test
    void isDrifting_GivenPeersBeyondThreshold_IsTrueAndWarnsWithSource() {
        monitor.onPeerClockSample("a", -60000);
        monitor.onPeerClockSample("b", -60000);
        monitor.onPeerClockSample("c", -60000);

        assertTrue(monitor.isDrifting());
        assertEquals(List.of("peers:-60"), log.warnings);
    }

    @Test
    void isDrifting_GivenPeersBackInRange_LogsRecovery() {
        monitor.onPeerClockSample("a", -60000);
        monitor.onPeerClockSample("b", -60000);
        monitor.onPeerClockSample("c", -60000);

        monitor.onPeerClockSample("a", 0);
        monitor.onPeerClockSample("b", 0);
        monitor.onPeerClockSample("c", 0);

        assertFalse(monitor.isDrifting());
        assertEquals(List.of(0), log.recoveries);
    }

    @Test
    void isDrifting_GivenBlocksInRangeButPeersBeyond_StaysDriftingWithoutRecovery() {
        monitor.onPeerClockSample("a", -60000);
        monitor.onPeerClockSample("b", -60000);
        monitor.onPeerClockSample("c", -60000);

        receiveTipBlocks(ClockDriftMonitorImpl.MIN_SAMPLES, 1);

        assertTrue(monitor.isDrifting());
        assertTrue(log.recoveries.isEmpty());
    }

    @Test
    void isDrifting_GivenZeroThresholdAndPeersFarOff_IsDisabled() {
        monitor = newMonitor(0);

        monitor.onPeerClockSample("a", -600000);
        monitor.onPeerClockSample("b", -600000);
        monitor.onPeerClockSample("c", -600000);

        assertFalse(monitor.isDrifting());
        assertTrue(log.warnings.isEmpty());
    }

    /** Lets {@code count} block intervals pass, each ending with a tip block seen with the given offset. */
    private void receiveTipBlocks(int count, int offset) {
        if (!referenceSent) {
            // the first block only sets the reference for the next one; it arrives with the same offset
            receiveAndPush(offset);
            referenceSent = true;
        }
        for (int i = 0; i < count; i++) {
            height++;
            blockTimestamp += BLOCK_INTERVAL;
            receiveAndPush(offset);
        }
    }

    private void receiveTipBlock(int offset) {
        height++;
        blockTimestamp += BLOCK_INTERVAL;
        receiveAndPush(offset);
    }

    /** The block id is its height: one honest block per height. */
    private void receiveAndPush(int offset) {
        time.set(blockTimestamp + offset);
        monitor.onBlockReceived(height);
        monitor.onBlockPushed(height, height, blockTimestamp);
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
        final List<String> warnings = new ArrayList<>();
        final List<Integer> recoveries = new ArrayList<>();

        @Override
        public void warn(String source, int offsetSeconds) {
            warnings.add(source + ":" + offsetSeconds);
        }

        @Override
        public void recovered(int offsetSeconds) {
            recoveries.add(offsetSeconds);
        }
    }
}
