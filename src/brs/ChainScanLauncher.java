package brs;

import brs.db.sql.ChainScanDatabase;
import brs.db.sql.SqlChainScanStore;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxCapacitorImpl;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.impl.ChainScanServiceImpl;
import brs.services.impl.chainscan.BlockIdCheck;
import brs.services.impl.chainscan.BlockPayloadCheck;
import brs.services.impl.chainscan.ChainCheck;
import brs.services.impl.chainscan.ChainLinkCheck;
import brs.services.impl.chainscan.ChainScanReportWriter;
import brs.services.impl.chainscan.ChainScanSummary;
import brs.services.impl.chainscan.ConsoleProgressBar;
import brs.services.impl.chainscan.Finding;
import brs.services.impl.chainscan.GenerationSignatureCheck;
import brs.services.impl.chainscan.Severity;
import brs.services.impl.chainscan.TransactionBlockConsistencyCheck;
import brs.services.impl.chainscan.TransactionRebuildCheck;
import brs.services.impl.chainscan.TransactionSignatureCheck;
import brs.util.Convert;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.cli.CommandLine;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import signumj.crypto.SignumCrypto;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs the read-only chain scan ({@code --verify-chain}): opens the database read-only, starts only
 * what the checks need and writes the report. See {@code docs/specs/chain-integrity-scan.md}.
 */
public final class ChainScanLauncher {

    public static final int EXIT_OK = 0;
    public static final int EXIT_FINDINGS = 1;
    public static final int EXIT_FAILED = 2;

    static final String OPT_VERIFY_CHAIN = "verify-chain";
    static final String OPT_FROM_HEIGHT = "from-height";
    static final String OPT_TO_HEIGHT = "to-height";
    static final String OPT_CHECKS = "checks";
    static final String OPT_THREADS = "threads";
    static final String OPT_REPORT = "report";

    private static final Logger logger = LoggerFactory.getLogger(ChainScanLauncher.class);
    private static final int CHUNK_SIZE = 1000;
    static final int MAX_THREADS = 256;
    private static final String TOOL_VERSION = "1";
    private static final List<String> KNOWN_LIMITATIONS = List.of(
            "No replay of account state: balances, ATs, assets and other derived tables are not checked.",
            "Ids of unsigned system transactions are not recomputed: their bytes depend on the node's height.",
            "Block signatures and PoC deadlines are not checked yet.");

    private final PropertyService propertyService;

    ChainScanLauncher(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    int run(CommandLine cmd) {
        Set<String> groups;
        int threads;
        try {
            groups = parseChecks(cmd.getOptionValue(OPT_CHECKS, "tx,block"));
            threads = parseThreads(cmd.getOptionValue(OPT_THREADS,
                    Integer.toString(Runtime.getRuntime().availableProcessors())));
        } catch (IllegalArgumentException e) {
            logger.error("Chain scan: {}", e.getMessage());
            return EXIT_FAILED;
        }
        try (ChainScanDatabase database = ChainScanDatabase.open(propertyService, threads + 2)) {
            MigrationInfo[] pending = database.pendingMigrations();
            if (pending.length > 0) {
                logger.error("Chain scan: the database lacks {} migrations of this build (first: {}). "
                        + "Start the node once with this build, or scan with the build that created the database.",
                        pending.length, pending[0].getVersion());
                return EXIT_FAILED;
            }
            SqlChainScanStore store = new SqlChainScanStore(database.getDataSource(), database.getDialect());
            int maxHeight = store.getMaxHeight();
            int fromHeight = Integer.parseInt(cmd.getOptionValue(OPT_FROM_HEIGHT, "0"));
            int toHeight = Integer.parseInt(cmd.getOptionValue(OPT_TO_HEIGHT, Integer.toString(maxHeight)));
            if (maxHeight < 0 || fromHeight < 0 || toHeight > maxHeight || toHeight < fromHeight) {
                logger.error("Chain scan: invalid height range {} to {}, the database holds 0 to {}",
                        fromHeight, toHeight, maxHeight);
                return EXIT_FAILED;
            }
            Blockchain blockchain = tipOnlyBlockchain(maxHeight);
            FluxCapacitor fluxCapacitor = new FluxCapacitorImpl(blockchain, propertyService);
            Signum.installForChainScan(propertyService, blockchain, fluxCapacitor);
            return scan(database, store, fluxCapacitor, groups, threads, fromHeight, toHeight, reportFile(cmd));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Chain scan interrupted");
            return EXIT_FAILED;
        } catch (Exception e) {
            logger.error("Chain scan failed", e);
            return EXIT_FAILED;
        }
    }

    private int scan(ChainScanDatabase database, SqlChainScanStore store, FluxCapacitor fluxCapacitor,
            Set<String> groups, int threads, int fromHeight, int toHeight, Path reportFile) throws Exception {
        List<ChainCheck> checks = checks(fluxCapacitor, groups);
        Path acceptFile = ChainScanReportWriter.acceptCandidatesFile(reportFile);
        logger.info("Chain scan of {} heights {} to {} with checks {} on {} threads, report {}",
                network(), fromHeight, toHeight, groups, threads, reportFile.toAbsolutePath());
        ChainScanSummary summary;
        List<Finding> firstFindings;
        try (ChainScanReportWriter writer = new ChainScanReportWriter(reportFile, acceptFile, network())) {
            writer.writeHeader(header(database, groups, checks, fromHeight, toHeight));
            summary = scanService(store, checks, threads).scan(fromHeight, toHeight, writer);
            writer.writeSummary(summary);
            firstFindings = writer.firstFindings();
        }
        logSummary(summary, firstFindings, reportFile, acceptFile);
        return summary.count(Severity.ERROR) > 0 ? EXIT_FINDINGS : EXIT_OK;
    }

    /**
     * In a terminal the progress is a bar redrawn twice a second; otherwise, e.g. under nohup or with
     * redirected output, a log line every 10 seconds.
     */
    private static ChainScanServiceImpl scanService(SqlChainScanStore store, List<ChainCheck> checks, int threads) {
        if (System.console() != null) {
            return new ChainScanServiceImpl(store, checks, threads, CHUNK_SIZE, new ConsoleProgressBar(System.out), 500);
        }
        return new ChainScanServiceImpl(store, checks, threads, CHUNK_SIZE);
    }

    private List<ChainCheck> checks(FluxCapacitor fluxCapacitor, Set<String> groups) {
        List<ChainCheck> all = List.of(
                new TransactionRebuildCheck(),
                new TransactionSignatureCheck(),
                new TransactionBlockConsistencyCheck(),
                new BlockIdCheck(),
                new BlockPayloadCheck(fluxCapacitor, propertyService.getInt(Props.CASH_BACK_FACTOR)),
                new ChainLinkCheck(),
                new GenerationSignatureCheck(SignumCrypto.getInstance()));
        return all.stream().filter(check -> groups.contains(check.group())).toList();
    }

    /**
     * Whether the arguments ask for the chain scan. Checked on the raw arguments, so that the scan is
     * chosen even if the other arguments don't parse; any prefix of the option counts, as commons-cli
     * matches partial long options.
     */
    public static boolean isRequested(String[] args) {
        String option = "--" + OPT_VERIFY_CHAIN;
        for (String arg : args) {
            if (arg.length() > 2 && option.startsWith(arg)) {
                return true;
            }
        }
        return false;
    }

    static int parseThreads(String value) {
        int threads;
        try {
            threads = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--threads must be a number: " + value);
        }
        if (threads < 1 || threads > MAX_THREADS) {
            throw new IllegalArgumentException("--threads must be between 1 and " + MAX_THREADS + ": " + threads);
        }
        return threads;
    }

    static Set<String> parseChecks(String value) {
        Set<String> groups = new LinkedHashSet<>();
        for (String group : value.toLowerCase().split(",")) {
            switch (group.trim()) {
                case "tx", "block" -> groups.add(group.trim());
                case "all" -> {
                    groups.add("tx");
                    groups.add("block");
                }
                case "poc", "basetarget" -> throw new IllegalArgumentException(
                        "check group '" + group.trim() + "' is not implemented yet");
                default -> throw new IllegalArgumentException("unknown check group '" + group.trim()
                        + "', use tx, block or all");
            }
        }
        return groups;
    }

    private Path reportFile(CommandLine cmd) {
        String defaultName = "chain-scan-" + network().toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".jsonl";
        return Paths.get(cmd.getOptionValue(OPT_REPORT, defaultName));
    }

    private String network() {
        return propertyService.getString(Props.NETWORK_NAME);
    }

    private JsonObject header(ChainScanDatabase database, Set<String> groups, List<ChainCheck> checks,
            int fromHeight, int toHeight) {
        JsonObject header = new JsonObject();
        header.addProperty("tool", "signum-node chain scan");
        header.addProperty("toolVersion", TOOL_VERSION);
        header.addProperty("nodeVersion", Signum.VERSION.toString());
        header.addProperty("network", network());
        header.addProperty("database", database.getDialect().getName());
        header.addProperty("schemaVersion", database.schemaVersion());
        header.addProperty("fromHeight", fromHeight);
        header.addProperty("toHeight", toHeight);
        JsonArray checkGroups = new JsonArray();
        groups.forEach(checkGroups::add);
        header.add("checkGroups", checkGroups);
        JsonArray checkClasses = new JsonArray();
        checks.forEach(check -> checkClasses.add(check.getClass().getSimpleName()));
        header.add("checks", checkClasses);
        JsonArray limitations = new JsonArray();
        KNOWN_LIMITATIONS.forEach(limitations::add);
        header.add("knownLimitations", limitations);
        return header;
    }

    private static void logSummary(ChainScanSummary summary, List<Finding> firstFindings, Path reportFile,
            Path acceptFile) {
        logger.info("Chain scan done: {} blocks, {} transactions in {} s; {} errors, {} warnings, {} infos",
                summary.blocks(), summary.transactions(), summary.durationMillis() / 1000,
                summary.count(Severity.ERROR), summary.count(Severity.WARN), summary.count(Severity.INFO));
        summary.findingCounts().forEach((key, count) -> logger.info("  {}: {}", key, count));
        for (Finding finding : firstFindings) {
            logger.info("  {} {} height {} block {}{}: {}", finding.severity(), finding.checkId(), finding.height(),
                    Convert.toUnsignedLong(finding.blockId()),
                    finding.transactionId() == null ? "" : " tx " + Convert.toUnsignedLong(finding.transactionId()),
                    finding.detail());
        }
        logger.info("Report: {}", reportFile.toAbsolutePath());
        logger.info("Accept candidates (TX_SIGNATURE): {}", acceptFile.toAbsolutePath());
    }

    /**
     * The flux capacitor asks the blockchain for the current height when no height is given. In scan
     * mode that is the height of the last stored block; nothing else of the blockchain is available.
     */
    static Blockchain tipOnlyBlockchain(int tipHeight) {
        return (Blockchain) Proxy.newProxyInstance(Blockchain.class.getClassLoader(), new Class<?>[] {Blockchain.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeight" -> tipHeight;
                    case "toString" -> "chain scan blockchain at height " + tipHeight;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                            "Blockchain." + method.getName() + " is not available in chain scan mode");
                });
    }
}
