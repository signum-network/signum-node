package brs.util;

import brs.props.PropertyService;
import brs.props.Props;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThreadPoolTest {

    private ThreadPool threadPool;
    private boolean wasRunning;

    @BeforeEach
    void setUp() {
        wasRunning = ThreadPool.running.get();
        PropertyService propertyService = mock(PropertyService.class);
        when(propertyService.getInt(Props.CPU_NUM_CORES)).thenReturn(1);
        when(propertyService.getInt(Props.BLOCK_PROCESS_THREAD_DELAY)).thenReturn(1);
        threadPool = new ThreadPool(propertyService);
    }

    @AfterEach
    void tearDown() {
        threadPool.shutdown();
        // shutdown() clears the static running flag; later tests in this JVM (the chain tests
        // start a node) would otherwise run with every background loop disabled
        ThreadPool.running.set(wasRunning);
    }

    @Test
    void testShutdown_GivenStartedPool_LeavesRunningFlagForTearDownToRestore() {
        threadPool.start(1);

        threadPool.shutdown();

        assertFalse(ThreadPool.running.get(), "shutdown() is expected to clear the static flag");
    }

    @Test
    void testGuarded_GivenJobThrowsError_DoesNotPropagateIt() {
        Runnable guarded = ThreadPool.guarded("Test", () -> {
            throw new OutOfMemoryError("Java heap space");
        });

        assertDoesNotThrow(guarded::run);
    }

    @Test
    void testGuarded_GivenJobThrowsException_DoesNotPropagateIt() {
        Runnable guarded = ThreadPool.guarded("Test", () -> {
            throw new IllegalStateException("boom");
        });

        assertDoesNotThrow(guarded::run);
    }

    @Test
    void testGuarded_GivenJobSucceeds_RunsIt() {
        AtomicInteger runs = new AtomicInteger();

        ThreadPool.guarded("Test", runs::incrementAndGet).run();

        assertEquals(1, runs.get());
    }

    @Test
    void testStart_GivenScheduledJobThrowsError_KeepsRunningIt() throws InterruptedException {
        CountDownLatch ranAgain = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        threadPool.scheduleThread("Test", () -> {
            if (runs.incrementAndGet() == 1) {
                throw new OutOfMemoryError("Java heap space");
            }
            ranAgain.countDown();
        }, 1, TimeUnit.MILLISECONDS);

        threadPool.start(1);

        assertTrue(ranAgain.await(5, TimeUnit.SECONDS), "job was not run again after an Error");
    }

    @Test
    void testStart_GivenMultiCoreJobThrowsException_KeepsRunningIt() throws InterruptedException {
        CountDownLatch ranAgain = new CountDownLatch(1);
        AtomicInteger runs = new AtomicInteger();
        threadPool.scheduleThreadCores(() -> {
            if (runs.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
            ranAgain.countDown();
        }, 1, TimeUnit.MILLISECONDS);

        threadPool.start(1);

        assertTrue(ranAgain.await(5, TimeUnit.SECONDS), "multi-core job was not run again after an exception");
    }
}
