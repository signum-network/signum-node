package brs.services.impl;

import brs.db.store.ChainScanStore;
import brs.services.impl.chainscan.BlockContext;
import brs.services.impl.chainscan.ChainCheck;
import brs.services.impl.chainscan.ChainScanProgress;
import brs.services.impl.chainscan.ChainScanSummary;
import brs.services.impl.chainscan.Finding;
import brs.services.impl.chainscan.ScannedBlock;
import brs.services.impl.chainscan.Severity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainScanServiceImplTest {

    /** Holds one empty block per height and records the chunks it was asked for. */
    private static final class FakeStore implements ChainScanStore {
        final List<int[]> chunks = Collections.synchronizedList(new ArrayList<>());
        int failAtHeight = -1;

        @Override
        public int getMaxHeight() {
            return 10_000;
        }

        @Override
        public List<BlockContext> loadBlocks(int fromHeight, int toHeight) {
            chunks.add(new int[] {fromHeight, toHeight});
            if (failAtHeight >= fromHeight && failAtHeight <= toHeight) {
                throw new IllegalStateException("database gone");
            }
            return IntStream.rangeClosed(fromHeight, toHeight)
                    .mapToObj(h -> new BlockContext(new ScannedBlock(h, h, h, h - 1, h + 1, 1L, null, null), List.of(),
                            null))
                    .toList();
        }
    }

    /** Reports every block at a height divisible by 100. */
    private static final ChainCheck EVERY_100TH = new ChainCheck() {
        @Override
        public String group() {
            return "block";
        }

        @Override
        public List<Finding> check(BlockContext context) {
            int height = context.block().height();
            return height % 100 == 0
                    ? List.of(new Finding("TEST", Severity.ERROR, height, height, null, null, "test"))
                    : List.of();
        }
    };

    @Test
    void scan_GivenRange_ChecksEveryBlockOnceInChunks() throws Exception {
        // Arrange
        FakeStore store = new FakeStore();
        List<Finding> findings = Collections.synchronizedList(new ArrayList<>());

        // Act
        ChainScanSummary summary = new ChainScanServiceImpl(store, List.of(EVERY_100TH), 4, 250)
                .scan(1, 1000, findings::add);

        // Assert
        assertEquals(1000, summary.blocks());
        assertEquals(10, findings.size());
        assertEquals(10L, summary.findingCounts().get("TEST/ERROR"));
        assertEquals(10L, summary.count(Severity.ERROR));
        assertEquals(4, store.chunks.size());
        assertTrue(store.chunks.stream().anyMatch(c -> c[0] == 751 && c[1] == 1000));
    }

    @Test
    void scan_ReportsFinalProgressToTheListener() throws Exception {
        // Arrange
        List<ChainScanProgress> snapshots = Collections.synchronizedList(new ArrayList<>());

        // Act
        new ChainScanServiceImpl(new FakeStore(), List.of(EVERY_100TH), 2, 100, snapshots::add, 60_000)
                .scan(1, 300, f -> { });

        // Assert: the interval is long, so only the final snapshot arrives
        assertEquals(1, snapshots.size());
        ChainScanProgress last = snapshots.get(0);
        assertEquals(300, last.blocksDone());
        assertEquals(300, last.totalBlocks());
        assertEquals(300, last.maxHeight());
        assertEquals(3, last.errors());
        assertEquals(1.0, last.fraction());
    }

    @Test
    void scan_GivenSingleHeight_ScansIt() throws Exception {
        ChainScanSummary summary = new ChainScanServiceImpl(new FakeStore(), List.of(EVERY_100TH), 2, 1000)
                .scan(500, 500, f -> { });
        assertEquals(1, summary.blocks());
        assertEquals(1L, summary.count(Severity.ERROR));
    }

    @Test
    void scan_GivenStoreFailure_ThrowsNamingTheChunk() {
        // Arrange
        FakeStore store = new FakeStore();
        store.failAtHeight = 600;

        // Act
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new ChainScanServiceImpl(store, List.of(EVERY_100TH), 2, 100).scan(0, 1000, f -> { }));

        // Assert
        assertTrue(e.getMessage().contains("heights 600 to 699"), e.getMessage());
    }

    @Test
    void scan_GivenInvalidRange_ThrowsIllegalArgumentException() {
        ChainScanServiceImpl service = new ChainScanServiceImpl(new FakeStore(), List.of(), 1, 10);
        assertThrows(IllegalArgumentException.class, () -> service.scan(5, 4, f -> { }));
        assertThrows(IllegalArgumentException.class, () -> service.scan(-1, 4, f -> { }));
    }

    @Test
    void constructor_GivenNoThreads_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new ChainScanServiceImpl(new FakeStore(), List.of(), 0, 10));
    }
}
