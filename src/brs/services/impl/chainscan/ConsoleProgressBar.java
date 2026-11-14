package brs.services.impl.chainscan;

import java.io.PrintStream;
import java.util.Locale;
import java.util.function.Consumer;

/** Draws the chain scan progress as one console line that is redrawn in place. */
public class ConsoleProgressBar implements Consumer<ChainScanProgress> {

    private static final int WIDTH = 30;

    private final PrintStream out;

    public ConsoleProgressBar(PrintStream out) {
        this.out = out;
    }

    @Override
    public synchronized void accept(ChainScanProgress progress) {
        out.print("\r" + render(progress));
        if (progress.blocksDone() >= progress.totalBlocks()) {
            out.println();
        }
        out.flush();
    }

    static String render(ChainScanProgress progress) {
        int filled = (int) Math.round(progress.fraction() * WIDTH);
        return String.format(Locale.ROOT, "[%s%s] %5.1f%%  height %,d  %,d of %,d blocks  %,d blocks/s  ETA %s  errors %,d  warnings %,d ",
                "#".repeat(filled), ".".repeat(WIDTH - filled), progress.fraction() * 100, Math.max(0, progress.maxHeight()),
                progress.blocksDone(), progress.totalBlocks(), Math.round(progress.blocksPerSecond()),
                duration(progress.etaSeconds()), progress.errors(), progress.warnings());
    }

    static String duration(long seconds) {
        if (seconds < 0) {
            return "--:--";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d", minutes, seconds % 60);
    }
}
