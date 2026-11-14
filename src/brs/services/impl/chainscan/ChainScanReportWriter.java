package brs.services.impl.chainscan;

import brs.util.Convert;
import com.google.gson.JsonObject;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Writes the chain scan report as JSON Lines: a header, one line per finding and a summary. The
 * {@code TX_SIGNATURE} findings also go to a CSV file, the input for the accept list.
 */
public class ChainScanReportWriter implements Consumer<Finding>, Closeable {

    /** How many findings {@link #firstFindings()} keeps for the console. */
    public static final int FIRST_FINDINGS = 20;

    private final String network;
    private final BufferedWriter report;
    private final BufferedWriter acceptCandidates;
    private final List<Finding> firstFindings = new ArrayList<>();

    public ChainScanReportWriter(Path reportFile, Path acceptCandidatesFile, String network) throws IOException {
        this.network = network;
        this.report = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8);
        this.acceptCandidates = Files.newBufferedWriter(acceptCandidatesFile, StandardCharsets.UTF_8);
        acceptCandidates.write("network;height;block_id;tx_id;full_hash");
        acceptCandidates.newLine();
    }

    /** The accept candidates file that belongs to a report file. */
    public static Path acceptCandidatesFile(Path reportFile) {
        return reportFile.resolveSibling(reportFile.getFileName() + ".accept-candidates.csv");
    }

    public synchronized void writeHeader(JsonObject header) {
        JsonObject line = new JsonObject();
        line.addProperty("type", "header");
        header.entrySet().forEach(e -> line.add(e.getKey(), e.getValue()));
        writeLine(line);
    }

    @Override
    public synchronized void accept(Finding finding) {
        writeLine(toJson(finding));
        if (firstFindings.size() < FIRST_FINDINGS) {
            firstFindings.add(finding);
        }
        if (TransactionSignatureCheck.TX_SIGNATURE.equals(finding.checkId())) {
            try {
                acceptCandidates.write(String.join(";", network, Integer.toString(finding.height()),
                        Convert.toUnsignedLong(finding.blockId()), Convert.toUnsignedLong(finding.transactionId()),
                        finding.fullHash()));
                acceptCandidates.newLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public synchronized void writeSummary(ChainScanSummary summary) {
        JsonObject line = new JsonObject();
        line.addProperty("type", "summary");
        line.addProperty("fromHeight", summary.fromHeight());
        line.addProperty("toHeight", summary.toHeight());
        line.addProperty("blocks", summary.blocks());
        line.addProperty("transactions", summary.transactions());
        line.addProperty("durationMillis", summary.durationMillis());
        JsonObject counts = new JsonObject();
        summary.findingCounts().forEach(counts::addProperty);
        line.add("findings", counts);
        writeLine(line);
    }

    public synchronized List<Finding> firstFindings() {
        return List.copyOf(firstFindings);
    }

    static JsonObject toJson(Finding finding) {
        JsonObject line = new JsonObject();
        line.addProperty("type", "finding");
        line.addProperty("check", finding.checkId());
        line.addProperty("severity", finding.severity().name());
        line.addProperty("height", finding.height());
        line.addProperty("blockId", Convert.toUnsignedLong(finding.blockId()));
        if (finding.transactionId() != null) {
            line.addProperty("txId", Convert.toUnsignedLong(finding.transactionId()));
            line.addProperty("fullHash", finding.fullHash());
        }
        line.addProperty("detail", finding.detail());
        return line;
    }

    private void writeLine(JsonObject line) {
        try {
            report.write(line.toString());
            report.newLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try (report; acceptCandidates) {
            report.flush();
            acceptCandidates.flush();
        }
    }
}
