package brs;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Times a rollback for the log: how long it took so far, and whether it is time for the next
 * progress line, so that a long pop-off does not look as if it stalled.
 */
final class PopOffTimer {

    /** The time between two progress lines of a long pop-off. */
    static final long PROGRESS_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final LongSupplier nanoClock;
    private final long progressIntervalNanos;
    private final long start;
    private long lastProgress;

    /**
     * @param nanoClock             a monotonic clock in nanoseconds, such as {@code System::nanoTime}
     * @param progressIntervalNanos the least time between two due progress lines
     */
    PopOffTimer(LongSupplier nanoClock, long progressIntervalNanos) {
        this.nanoClock = nanoClock;
        this.progressIntervalNanos = progressIntervalNanos;
        this.start = nanoClock.getAsLong();
        this.lastProgress = start;
    }

    static PopOffTimer start() {
        return new PopOffTimer(System::nanoTime, PROGRESS_INTERVAL_NANOS);
    }

    /** @return the time since the timer started, formatted for the log */
    String elapsed() {
        return format(nanoClock.getAsLong() - start);
    }

    /** @return true at most once per progress interval, counted from the start or the last due line */
    boolean isProgressDue() {
        long now = nanoClock.getAsLong();
        if (now - lastProgress < progressIntervalNanos) {
            return false;
        }
        lastProgress = now;
        return true;
    }

    static String format(long nanos) {
        long millis = TimeUnit.NANOSECONDS.toMillis(Math.max(nanos, 0));
        if (millis < 1_000) {
            return millis + " ms";
        }
        if (millis < 60_000) {
            return String.format(Locale.ROOT, "%.1f s", millis / 1_000.0);
        }
        long seconds = millis / 1_000;
        if (seconds < 3_600) {
            return String.format(Locale.ROOT, "%d min %02d s", seconds / 60, seconds % 60);
        }
        return String.format(Locale.ROOT, "%d h %02d min", seconds / 3_600, (seconds % 3_600) / 60);
    }
}
