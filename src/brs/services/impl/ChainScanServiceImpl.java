package brs.services.impl;

import brs.db.store.ChainScanStore;
import brs.services.ChainScanService;
import brs.services.impl.chainscan.BlockContext;
import brs.services.impl.chainscan.ChainCheck;
import brs.services.impl.chainscan.ChainScanProgress;
import brs.services.impl.chainscan.ChainScanSummary;
import brs.services.impl.chainscan.Finding;
import brs.services.impl.chainscan.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Splits the height range into chunks and checks them on a thread pool. At most two chunks per
 * thread are loaded at a time, so memory stays bounded however long the chain is.
 */
public class ChainScanServiceImpl implements ChainScanService {

    private static final Logger logger = LoggerFactory.getLogger(ChainScanServiceImpl.class);

    private final ChainScanStore store;
    private final List<ChainCheck> checks;
    private final int threads;
    private final int chunkSize;
    private final Consumer<ChainScanProgress> progressListener;
    private final long progressIntervalMillis;

    /**
     * @param progressListener receives a progress snapshot every {@code progressIntervalMillis} and once
     *                         at the end
     */
    public ChainScanServiceImpl(ChainScanStore store, List<ChainCheck> checks, int threads, int chunkSize,
            Consumer<ChainScanProgress> progressListener, long progressIntervalMillis) {
        if (threads < 1 || chunkSize < 1 || progressIntervalMillis < 1) {
            throw new IllegalArgumentException("threads, chunk size and progress interval must be positive: "
                    + threads + ", " + chunkSize + ", " + progressIntervalMillis);
        }
        this.store = store;
        this.checks = List.copyOf(checks);
        this.threads = threads;
        this.chunkSize = chunkSize;
        this.progressListener = progressListener;
        this.progressIntervalMillis = progressIntervalMillis;
    }

    /** Logs the progress every 10 seconds. */
    public ChainScanServiceImpl(ChainScanStore store, List<ChainCheck> checks, int threads, int chunkSize) {
        this(store, checks, threads, chunkSize, ChainScanServiceImpl::log, 10_000);
    }

    /** A progress listener that logs one line per snapshot. */
    public static void log(ChainScanProgress progress) {
        logger.info("Chain scan: {} of {} blocks (up to height {}), {} blocks/s, ETA {} s, {} errors, {} warnings",
                progress.blocksDone(), progress.totalBlocks(), progress.maxHeight(),
                Math.round(progress.blocksPerSecond()), progress.etaSeconds(), progress.errors(), progress.warnings());
    }

    @Override
    public ChainScanSummary scan(int fromHeight, int toHeight, Consumer<Finding> findingSink)
            throws InterruptedException {
        if (fromHeight < 0 || toHeight < fromHeight) {
            throw new IllegalArgumentException("invalid height range " + fromHeight + " to " + toHeight);
        }
        Progress progress = new Progress(toHeight - fromHeight + 1L);
        ExecutorService workers = Executors.newFixedThreadPool(threads);
        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();
        reporter.scheduleAtFixedRate(() -> progressListener.accept(progress.snapshot()), progressIntervalMillis,
                progressIntervalMillis, TimeUnit.MILLISECONDS);
        Semaphore loadedChunks = new Semaphore(threads * 2);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        try {
            for (long start = fromHeight; start <= toHeight && failure.get() == null; start += chunkSize) {
                int chunkFrom = (int) start;
                int chunkTo = (int) Math.min(start + chunkSize - 1, toHeight);
                loadedChunks.acquire();
                workers.execute(() -> {
                    try {
                        scanChunk(chunkFrom, chunkTo, findingSink, progress);
                    } catch (RuntimeException e) {
                        failure.compareAndSet(null, new IllegalStateException(
                                "scan of heights " + chunkFrom + " to " + chunkTo + " failed: " + e, e));
                    } finally {
                        loadedChunks.release();
                    }
                });
            }
            workers.shutdown();
            while (!workers.awaitTermination(1, TimeUnit.MINUTES)) {
                logger.debug("waiting for the chain scan workers");
            }
        } finally {
            workers.shutdownNow();
            reporter.shutdownNow();
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        progressListener.accept(progress.snapshot());
        return progress.summary(fromHeight, toHeight);
    }

    private void scanChunk(int fromHeight, int toHeight, Consumer<Finding> findingSink, Progress progress) {
        for (BlockContext context : store.loadBlocks(fromHeight, toHeight)) {
            for (ChainCheck check : checks) {
                for (Finding finding : check.check(context)) {
                    progress.found(finding);
                    findingSink.accept(finding);
                }
            }
            progress.scanned(context);
        }
    }

    /** Counters shared by the workers. */
    private static final class Progress {
        private final long totalBlocks;
        private final long startNanos = System.nanoTime();
        private final AtomicLong blocks = new AtomicLong();
        private final AtomicLong transactions = new AtomicLong();
        private final AtomicLong maxHeight = new AtomicLong(-1);
        private final Map<String, LongAdder> findings = new ConcurrentHashMap<>();

        private Progress(long totalBlocks) {
            this.totalBlocks = totalBlocks;
        }

        void scanned(BlockContext context) {
            blocks.incrementAndGet();
            transactions.addAndGet(context.transactions().size());
            maxHeight.accumulateAndGet(context.block().height(), Math::max);
        }

        void found(Finding finding) {
            findings.computeIfAbsent(finding.checkId() + "/" + finding.severity(), k -> new LongAdder()).increment();
        }

        Map<String, Long> findingCounts() {
            Map<String, Long> counts = new TreeMap<>();
            findings.forEach((key, count) -> counts.put(key, count.sum()));
            return counts;
        }

        ChainScanProgress snapshot() {
            double seconds = Math.max(1e-3, (System.nanoTime() - startNanos) / 1e9);
            long done = blocks.get();
            double rate = done / seconds;
            long etaSeconds = rate > 0 ? Math.round((totalBlocks - done) / rate) : -1;
            return new ChainScanProgress(done, totalBlocks, maxHeight.get(), rate, etaSeconds,
                    count(Severity.ERROR), count(Severity.WARN));
        }

        private long count(Severity severity) {
            return findings.entrySet().stream()
                    .filter(e -> e.getKey().endsWith("/" + severity.name()))
                    .mapToLong(e -> e.getValue().sum())
                    .sum();
        }

        ChainScanSummary summary(int fromHeight, int toHeight) {
            return new ChainScanSummary(fromHeight, toHeight, blocks.get(), transactions.get(), findingCounts(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        }
    }
}
