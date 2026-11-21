package brs.services;

import com.google.gson.JsonObject;

/** How this node is doing, for the web UI's dashboard: uptime, database, free disk, reachability. */
public interface NodeHealthService {

    /**
     * @return {@code startedAt}, {@code databaseType}, {@code databaseSizeBytes}, {@code freeDiskBytes},
     * {@code freeDiskScope}, {@code lastInboundPeerAt}, {@code lastLocalInboundPeerAt},
     * {@code peerServerRunning}, {@code announcesAddress}; shared between callers, do not modify
     */
    JsonObject getNodeHealth();
}
