package brs.services.impl;

import brs.db.sql.dialects.DatabaseInstance;
import brs.services.NodeHealthService;
import brs.services.PeerServerFacts;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.jooq.SQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

/**
 * Builds the node health at most once per {@link #CACHE_MS}: the database size can be an expensive
 * query on MariaDB/Postgres, and on a public node every dashboard visitor asks for it.
 */
public class NodeHealthServiceImpl implements NodeHealthService {

    static final long CACHE_MS = 60_000L;

    private static final Logger logger = LoggerFactory.getLogger(NodeHealthServiceImpl.class);

    private final DatabaseInstance database;
    private final PeerServerFacts peers;
    /** milliseconds on any clock that only moves forward; used for the cache age only */
    private final LongSupplier clock;
    private final long startedAtMillis;
    private final ToLongFunction<Path> usableSpace;

    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile JsonObject cached;
    private volatile long cachedAt;

    public NodeHealthServiceImpl(DatabaseInstance database, PeerServerFacts peers) {
        // cache age on a monotonic clock: a wall-clock step back must not freeze the cached value
        this(database, peers, () -> System.nanoTime() / 1_000_000L, ManagementFactory.getRuntimeMXBean().getStartTime(),
                path -> path.toFile().getUsableSpace());
    }

    NodeHealthServiceImpl(DatabaseInstance database, PeerServerFacts peers, LongSupplier clock, long startedAtMillis,
                          ToLongFunction<Path> usableSpace) {
        this.database = database;
        this.peers = peers;
        this.clock = clock;
        this.startedAtMillis = startedAtMillis;
        this.usableSpace = usableSpace;
    }

    /**
     * One caller refreshes an expired value; while it does, everyone else gets the last value at once
     * instead of waiting on a slow database. Only the very first call makes others wait.
     */
    @Override
    public JsonObject getNodeHealth() {
        JsonObject current = cached;
        if (current != null && !expired()) {
            return current;
        }
        if (current != null && !refreshLock.tryLock()) {
            return current;
        }
        if (current == null) {
            refreshLock.lock();
        }
        try {
            if (cached == null || expired()) {
                cached = build();
                cachedAt = clock.getAsLong();
            }
            return cached;
        } finally {
            refreshLock.unlock();
        }
    }

    private boolean expired() {
        return clock.getAsLong() - cachedAt >= CACHE_MS;
    }

    private JsonObject build() {
        JsonObject health = new JsonObject();
        health.addProperty("startedAt", startedAtMillis);
        health.addProperty("databaseType", databaseType(database.getDialect()));
        addOptional(health, "databaseSizeBytes", database.getDatabaseSizeBytes());
        Optional<Path> dbFile = database.getDatabaseFile();
        Path volume = dbFile.map(Path::getParent).orElse(Path.of("").toAbsolutePath());
        addOptional(health, "freeDiskBytes", freeSpace(volume));
        health.addProperty("freeDiskScope", dbFile.isPresent() ? "database" : "node");
        addOptional(health, "lastInboundPeerAt", peers.lastPublicInboundMillis());
        addOptional(health, "lastLocalInboundPeerAt", peers.lastLocalInboundMillis());
        health.addProperty("peerServerRunning", peers.peerServerRunning());
        health.addProperty("announcesAddress", peers.announcesAddress());
        return health;
    }

    private OptionalLong freeSpace(Path volume) {
        try {
            return OptionalLong.of(usableSpace.applyAsLong(volume));
        } catch (RuntimeException e) {
            logger.debug("Could not determine the free disk space", e);
            return OptionalLong.empty();
        }
    }

    private static String databaseType(SQLDialect dialect) {
        if (dialect == null) {
            return "unknown";
        }
        return switch (dialect.family()) {
            case SQLITE -> "sqlite";
            case MARIADB, MYSQL -> "mariadb";
            case POSTGRES -> "postgres";
            default -> dialect.getName().toLowerCase();
        };
    }

    private static void addOptional(JsonObject json, String field, OptionalLong value) {
        if (value.isPresent()) {
            json.addProperty(field, value.getAsLong());
        } else {
            json.add(field, JsonNull.INSTANCE);
        }
    }
}
