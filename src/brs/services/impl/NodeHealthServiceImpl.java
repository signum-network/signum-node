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
    private final LongSupplier clock;
    private final long startedAtMillis;
    private final ToLongFunction<Path> usableSpace;

    private JsonObject cached;
    private long cachedAt;

    public NodeHealthServiceImpl(DatabaseInstance database, PeerServerFacts peers) {
        this(database, peers, System::currentTimeMillis, ManagementFactory.getRuntimeMXBean().getStartTime(),
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

    @Override
    public synchronized JsonObject getNodeHealth() {
        long now = clock.getAsLong();
        if (cached == null || now - cachedAt >= CACHE_MS) {
            cached = build();
            cachedAt = now;
        }
        return cached;
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
