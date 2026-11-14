package brs.services.impl.chainscan;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleProgressBarTest {

    @Test
    void render_GivenHalfDone_FillsHalfTheBar() {
        String line = ConsoleProgressBar.render(new ChainScanProgress(500, 1000, 499, 250.0, 2, 1, 0));
        assertTrue(line.startsWith("[###############...............]  50.0%"), line);
        assertTrue(line.contains("ETA 0:02"), line);
        assertTrue(line.contains("errors 1"), line);
    }

    @Test
    void duration_FormatsMinutesAndHours() {
        assertEquals("--:--", ConsoleProgressBar.duration(-1));
        assertEquals("1:05", ConsoleProgressBar.duration(65));
        assertEquals("2:00:07", ConsoleProgressBar.duration(7207));
    }

    @Test
    void accept_RedrawsTheLineAndEndsItWhenDone() {
        // Arrange
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleProgressBar bar = new ConsoleProgressBar(new PrintStream(buffer, true, StandardCharsets.UTF_8));

        // Act
        bar.accept(new ChainScanProgress(1, 2, 0, 1.0, 1, 0, 0));
        String running = buffer.toString(StandardCharsets.UTF_8);
        bar.accept(new ChainScanProgress(2, 2, 1, 1.0, 0, 0, 0));

        // Assert
        assertTrue(running.startsWith("\r"));
        assertFalse(running.endsWith(System.lineSeparator()));
        assertTrue(buffer.toString(StandardCharsets.UTF_8).endsWith(System.lineSeparator()));
    }
}
