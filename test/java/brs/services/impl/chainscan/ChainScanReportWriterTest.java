package brs.services.impl.chainscan;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChainScanReportWriterTest {

    @TempDir
    Path folder;

    @Test
    void writer_WritesHeaderFindingsAndSummaryAndAcceptCandidates() throws Exception {
        // Arrange
        Path report = folder.resolve("scan.jsonl");
        Path accept = ChainScanReportWriter.acceptCandidatesFile(report);
        Finding signature = new Finding(TransactionSignatureCheck.TX_SIGNATURE, Severity.ERROR, 12, -1L, 5L, "ab12",
                "invalid signature");
        Finding link = new Finding(ChainLinkCheck.CHAIN_LINK, Severity.ERROR, 13, 7L, null, null, "broken link");
        JsonObject header = new JsonObject();
        header.addProperty("network", "Signum-TESTNET");

        // Act
        try (ChainScanReportWriter writer = new ChainScanReportWriter(report, accept, "Signum-TESTNET")) {
            writer.writeHeader(header);
            writer.accept(signature);
            writer.accept(link);
            writer.writeSummary(new ChainScanSummary(0, 13, 14, 3, Map.of("TX_SIGNATURE/ERROR", 1L), 99));
        }

        // Assert
        List<String> lines = Files.readAllLines(report);
        assertEquals(4, lines.size());
        assertEquals("header", type(lines.get(0)));
        JsonObject finding = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        assertEquals("TX_SIGNATURE", finding.get("check").getAsString());
        assertEquals("18446744073709551615", finding.get("blockId").getAsString());
        assertEquals("5", finding.get("txId").getAsString());
        assertFalse(JsonParser.parseString(lines.get(2)).getAsJsonObject().has("txId"));
        assertEquals("summary", type(lines.get(3)));
        assertEquals(List.of("network;height;block_id;tx_id;full_hash", "Signum-TESTNET;12;18446744073709551615;5;ab12"),
                Files.readAllLines(accept));
    }

    @Test
    void firstFindings_KeepsAtMostTwenty() throws Exception {
        // Arrange
        Path report = folder.resolve("many.jsonl");
        try (ChainScanReportWriter writer = new ChainScanReportWriter(report,
                ChainScanReportWriter.acceptCandidatesFile(report), "net")) {
            // Act
            for (int i = 0; i < 25; i++) {
                writer.accept(new Finding("X", Severity.WARN, i, i, null, null, "x"));
            }

            // Assert
            assertEquals(ChainScanReportWriter.FIRST_FINDINGS, writer.firstFindings().size());
        }
    }

    @Test
    void acceptCandidatesFile_SitsNextToTheReport() {
        assertEquals(folder.resolve("a.jsonl.accept-candidates.csv"),
                ChainScanReportWriter.acceptCandidatesFile(folder.resolve("a.jsonl")));
    }

    private static String type(String line) {
        return JsonParser.parseString(line).getAsJsonObject().get("type").getAsString();
    }
}
