package brs.peer;

import brs.*;
import brs.fluxcapacitor.FluxValues;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.AccountService;
import brs.services.TimeService;
import brs.util.JSON;
import brs.util.Listener;
import brs.util.Listeners;
import brs.util.ThreadPool;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.DoSFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static brs.peer.Peer.isHigherOrEqualVersion;
import static brs.props.Props.P2P_ENABLE_TX_REBROADCAST;
import static brs.props.Props.P2P_SEND_TO_LIMIT;
import static brs.util.JSON.prepareRequest;

public final class Peers {

    private static final Logger logger = LoggerFactory.getLogger(Peers.class);

    public static boolean isSupportedUserAgent(String header) {
        if (header == null || header.isEmpty() || !header.trim().startsWith("BRS/")) {
            return false;
        } else {
            try {
                return isHigherOrEqualVersion(Signum.getFluxCapacitor().getValue(FluxValues.MIN_PEER_VERSION),
                        Version.parse(header.trim().substring("BRS/".length())));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    public enum Event {
        BLACKLIST, UNBLACKLIST, DEACTIVATE, REMOVE,
        DOWNLOADED_VOLUME, UPLOADED_VOLUME, WEIGHT,
        ADDED_ACTIVE_PEER, CHANGED_ACTIVE_PEER,
        NEW_PEER
    }

    static final int LOGGING_MASK_EXCEPTIONS = 1;
    static final int LOGGING_MASK_NON200_RESPONSES = 2;
    static final int LOGGING_MASK_200_RESPONSES = 4;
    static int communicationLoggingMask;

    private static final Random r = new Random();

    static Set<String> wellKnownPeers;
    static Set<String> knownBlacklistedPeers;

    private static int connectWellKnownFirst;
    private static boolean connectWellKnownFinished;

    static Set<String> rebroadcastPeers;

    static int connectTimeout;
    static int readTimeout;
    static int blacklistingPeriod;
    static boolean getMorePeers;

    private static String myPlatform;
    private static String myAddress;
    private static int myPeerServerPort;
    private static boolean useUpnp;
    private static boolean shareMyAddress;
    private static int maxNumberOfConnectedPublicPeers;
    private static int sendToPeersLimit;
    private static boolean usePeersDb;
    private static boolean savePeers;
    private static int getMorePeersThreshold;
    private static String dumpPeersVersion;
    private static int lastSavedPeers;

    static JsonElement myPeerInfoRequest;
    static JsonElement myPeerInfoResponse;

    public static Listeners<Peer, Event> listeners = new Listeners<>();

    private static final ConcurrentMap<String, Peer> peers = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> announcedAddresses = new ConcurrentHashMap<>();

    private static final Collection<Peer> allPeers = Collections.unmodifiableCollection(peers.values());

    private static final ExecutorService sendBlocksToPeersService = Executors.newCachedThreadPool();
    private static final ExecutorService blocksSendingService = Executors.newFixedThreadPool(10);

    private static TimeService timeService;
    private static PropertyService propertyService;

    public static void init(TimeService timeService, AccountService accountService, Blockchain blockchain,
            TransactionProcessor transactionProcessor,
            BlockchainProcessor blockchainProcessor, PropertyService propertyService, ThreadPool threadPool) {
        Peers.timeService = timeService;
        Peers.propertyService = propertyService;

        myPlatform = propertyService.getString(Props.P2P_MY_PLATFORM);
        if (propertyService.getString(Props.P2P_MY_ADDRESS) != null
                && propertyService.getString(Props.P2P_MY_ADDRESS).trim().isEmpty()
                && Init.gateway != null) {
            String externalIPAddress = null;
            try {
                externalIPAddress = Init.gateway.getExternalIPAddress();
            } catch (IOException | SAXException e) {
                logger.info("Can't get gateways IP adress");
            }
            myAddress = externalIPAddress;
        } else {
            myAddress = propertyService.getString(Props.P2P_MY_ADDRESS);
        }

        myPeerServerPort = propertyService.getInt(Props.P2P_PORT);
        useUpnp = propertyService.getBoolean(Props.P2P_UPNP);
        shareMyAddress = propertyService.getBoolean(Props.P2P_SHARE_MY_ADDRESS)
                && !Signum.getPropertyService().getBoolean(Props.DEV_OFFLINE);

        JsonObject json = new JsonObject();
        if (myAddress != null && !myAddress.isEmpty()) {
            try {
                URI uri = new URI("http://" + myAddress.trim());
                String host = uri.getHost();
                int port = uri.getPort();
                if (port >= 0) {
                    json.addProperty("announcedAddress", myAddress);
                } else {
                    json.addProperty("announcedAddress", host + ":" + myPeerServerPort);
                }
            } catch (URISyntaxException e) {
                logger.info("Your announce address is invalid: {}", myAddress);
                throw new RuntimeException(e.toString(), e);
            }
        }

        json.addProperty("application", Signum.APPLICATION);
        json.addProperty("version", Signum.VERSION.toString());
        json.addProperty("platform", Peers.myPlatform);
        json.addProperty("shareAddress", Peers.shareMyAddress);
        json.addProperty("networkName", propertyService.getString(Props.NETWORK_NAME));
        if (logger.isDebugEnabled()) {
            logger.debug("My peer info: {}", JSON.toJsonString(json));
        }
        myPeerInfoResponse = JSON.cloneJson(json);
        json.addProperty("requestType", "getInfo");
        myPeerInfoRequest = prepareRequest(json);

        if (propertyService.getBoolean(P2P_ENABLE_TX_REBROADCAST)) {
            rebroadcastPeers = Collections
                    .unmodifiableSet(new HashSet<>(propertyService.getStringList(Props.P2P_REBROADCAST_TO)));
        } else {
            rebroadcastPeers = Collections.emptySet();
        }

        List<String> wellKnownPeersList = propertyService.getStringList(Props.P2P_BOOTSTRAP_PEERS);

        for (String rePeer : rebroadcastPeers) {
            if (!wellKnownPeersList.contains(rePeer)) {
                wellKnownPeersList.add(rePeer);
            }
        }
        if (wellKnownPeersList.isEmpty() || Signum.getPropertyService().getBoolean(Props.DEV_OFFLINE)) {
            wellKnownPeers = Collections.emptySet();
        } else {
            wellKnownPeers = Collections.unmodifiableSet(new HashSet<>(wellKnownPeersList));
        }

        connectWellKnownFirst = propertyService.getInt(Props.P2P_NUM_BOOTSTRAP_CONNECTIONS);
        connectWellKnownFinished = (connectWellKnownFirst == 0);

        List<String> knownBlacklistedPeersList = propertyService.getStringList(Props.P2P_BLACKLISTED_PEERS);
        if (knownBlacklistedPeersList.isEmpty()) {
            knownBlacklistedPeers = Collections.emptySet();
        } else {
            knownBlacklistedPeers = Collections.unmodifiableSet(new HashSet<>(knownBlacklistedPeersList));
        }

        maxNumberOfConnectedPublicPeers = propertyService.getInt(Props.P2P_MAX_CONNECTIONS);
        logger.info("P2P max connections: {}", maxNumberOfConnectedPublicPeers);
        connectTimeout = propertyService.getInt(Props.P2P_TIMEOUT_CONNECT_MS);
        readTimeout = propertyService.getInt(Props.P2P_TIMEOUT_READ_MS);

        blacklistingPeriod = propertyService.getInt(Props.P2P_BLACKLISTING_TIME_MS);
        communicationLoggingMask = propertyService.getInt(Props.BRS_COMMUNICATION_LOGGING_MASK);
        sendToPeersLimit = propertyService.getInt(P2P_SEND_TO_LIMIT);
        usePeersDb = propertyService.getBoolean(Props.P2P_USE_PEERS_DB)
                && !Signum.getPropertyService().getBoolean(Props.DEV_OFFLINE);
        savePeers = usePeersDb && propertyService.getBoolean(Props.P2P_SAVE_PEERS);
        getMorePeers = propertyService.getBoolean(Props.P2P_GET_MORE_PEERS);
        getMorePeersThreshold = propertyService.getInt(Props.P2P_GET_MORE_PEERS_THRESHOLD);
        dumpPeersVersion = propertyService.getString(Props.DEV_DUMP_PEERS_VERSION);

        final List<Future<String>> unresolvedPeers = Collections.synchronizedList(new ArrayList<>());

        threadPool.runBeforeStart(new Runnable() {

            private void loadPeers(Collection<String> addresses) {
                for (final String address : addresses) {
                    Future<String> unresolvedAddress = sendBlocksToPeersService.submit(() -> {
                        Peer peer = Peers.addPeer(address);
                        return peer == null ? address : null;
                    });
                    unresolvedPeers.add(unresolvedAddress);
                }
            }

            @Override
            public void run() {
                if (!wellKnownPeers.isEmpty()) {
                    loadPeers(wellKnownPeers);
                }
                if (usePeersDb) {
                    logger.debug("Loading known peers from the database...");
                    loadPeers(Signum.getDbs().getPeerDb().loadPeers());
                }
                lastSavedPeers = peers.size();
            }
        }, false);

        threadPool.runAfterStart(() -> {
            for (Future<String> unresolvedPeer : unresolvedPeers) {
                try {
                    String badAddress = unresolvedPeer.get(5, TimeUnit.SECONDS);
                    if (badAddress != null) {
                        logger.debug("Failed to resolve peer address: {}", badAddress);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    logger.debug("Failed to add peer", e);
                } catch (TimeoutException ignored) {
                }
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Known peers: {}", peers.size());
            }
        });

        Init.init(timeService, accountService, blockchain, transactionProcessor, blockchainProcessor, propertyService,
                threadPool);

        if (!Signum.getPropertyService().getBoolean(Props.DEV_OFFLINE)) {
            threadPool.scheduleThread("PeerConnecting", Peers.peerConnectingThread, 5);
            threadPool.scheduleThread("PeerUnBlacklisting", Peers.peerUnBlacklistingThread, 1);
            if (Peers.getMorePeers) {
                threadPool.scheduleThread("GetMorePeers", Peers.getMorePeersThread, 5);
            }
        }

    }

    private static class Init {

        private static Server peerServer;
        private static GatewayDevice gateway;
        private static Integer port;

        static void init(TimeService timeService, AccountService accountService, Blockchain blockchain,
                TransactionProcessor transactionProcessor,
                BlockchainProcessor blockchainProcessor, PropertyService propertyService, ThreadPool threadPool) {
            if (Peers.shareMyAddress) {
                port = Peers.myPeerServerPort;
                if (useUpnp) {
                    GatewayDiscover gatewayDiscover = new GatewayDiscover();
                    gatewayDiscover.setTimeout(2000);
                    try {
                        gatewayDiscover.discover();
                    } catch (IOException | SAXException | ParserConfigurationException ignored) {
                    }
                    logger.trace("Looking for Gateway Devices");
                    gateway = gatewayDiscover.getValidGateway();

                    Runnable gwDiscover = () -> {
                        if (gateway != null) {
                            GatewayDevice.setHttpReadTimeout(2000);
                            try {
                                InetAddress localAddress = gateway.getLocalAddress();
                                String externalIPAddress = gateway.getExternalIPAddress();
                                if (logger.isInfoEnabled()) {
                                    logger.info("Attempting to map {}:{} -> {}:{} on Gateway {} ({})",
                                            externalIPAddress, port, localAddress, port, gateway.getModelName(),
                                            gateway.getModelDescription());
                                }
                                PortMappingEntry portMapping = new PortMappingEntry();
                                if (gateway.getSpecificPortMappingEntry(port, "TCP", portMapping)) {
                                    logger.info("Port was already mapped. Aborting test.");
                                } else {
                                    if (gateway.addPortMapping(port, port, localAddress.getHostAddress(), "TCP",
                                            "burstcoin")) {
                                        logger.info("UPnP Mapping successful");
                                    } else {
                                        logger.warn("UPnP Mapping was denied!");
                                    }
                                }
                            } catch (IOException | SAXException e) {
                                logger.error("Can't start UPnP", e);
                            }
                        }
                    };
                    if (gateway != null) {
                        new Thread(gwDiscover).start();
                    } else {
                        logger.warn("Tried to establish UPnP, but it was denied by the network.");
                    }
                }

                peerServer = new Server();
                ServerConnector connector = new ServerConnector(peerServer);
                connector.setPort(port);
                final String host = propertyService.getString(Props.P2P_LISTEN);
                connector.setHost(host);
                connector.setIdleTimeout(propertyService.getInt(Props.P2P_TIMEOUT_IDLE_MS));
                connector.setReuseAddress(true);
                peerServer.addConnector(connector);

                ServletHolder peerServletHolder = new ServletHolder(
                        new PeerServlet(timeService, accountService, blockchain,
                                transactionProcessor, blockchainProcessor, propertyService));
                boolean isGzipEnabled = propertyService.getBoolean(Props.JETTY_P2P_GZIP_FILTER);
                peerServletHolder.setInitParameter("isGzipEnabled", Boolean.toString(isGzipEnabled));

                ServletHandler peerHandler = new ServletHandler();
                peerHandler.addServletWithMapping(peerServletHolder, "/*");

                if (propertyService.getBoolean(Props.JETTY_P2P_DOS_FILTER)) {
                    FilterHolder dosFilterHolder = peerHandler.addFilterWithMapping(DoSFilter.class, "/*",
                            FilterMapping.DEFAULT);
                    dosFilterHolder.setInitParameter("maxRequestsPerSec",
                            propertyService.getString(Props.JETTY_P2P_DOS_FILTER_MAX_REQUESTS_PER_SEC));
                    dosFilterHolder.setInitParameter("throttledRequests",
                            propertyService.getString(Props.JETTY_P2P_DOS_FILTER_THROTTLED_REQUESTS));
                    dosFilterHolder.setInitParameter("delayMs",
                            propertyService.getString(Props.JETTY_P2P_DOS_FILTER_DELAY_MS));
                    dosFilterHolder.setInitParameter("maxWaitMs",
                            propertyService.getString(Props.JETTY_P2P_DOS_FILTER_MAX_WAIT_MS));
                    dosFilterHolder.setInitParameter("maxRequestMs",
                            propertyService.getString(Props.JETTY_P2P_DOS_FILTER_MAX_REQUEST_MS));
                    dosFilterHolder.setInitParameter("maxthrottleMs",
                            propertyService.getString(Props.JETTY_P2P_DOS_FILTER_THROTTLE_MS));
                    dosFilterHolder.setInitParameter("maxIdleTrackerMs",
                            propertyService.getString(Props.JETTY_P2P_DOS_FILTER_MAX_IDLE_TRACKER_MS));
                    dosFilterHolder.setInitParameter("trackSessions",
                            propertyService.getString(Props.JETTY_P2P_DOS_FILTER_TRACK_SESSIONS));
                    dosFilterHolder.setInitParameter("insertHeaders",
                            propertyService.getString(Props.JETTY_P2P_DOS_FILTER_INSERT_HEADERS));
                    dosFilterHolder.setInitParameter("remotePort",
                            propertyService.getString(Props.JETTY_P2P_DOS_FILTER_REMOTE_PORT));
                    dosFilterHolder.setInitParameter("ipWhitelist",
                            propertyService.getString(Props.JETTY_P2P_DOS_FILTER_IP_WHITELIST));
                    dosFilterHolder.setInitParameter("managedAttr",
                            propertyService.getString(Props.JETTY_P2P_DOS_FILTER_MANAGED_ATTR));
                    dosFilterHolder.setAsyncSupported(true);
                }

                if (isGzipEnabled) {
                    GzipHandler gzipHandler = new GzipHandler();
                    gzipHandler.setIncludedMethods("GET,POST");
                    gzipHandler.setMinGzipSize(propertyService.getInt(Props.JETTY_P2P_GZIP_FILTER_MIN_GZIP_SIZE));
                    gzipHandler.setHandler(peerHandler);
                    peerServer.setHandler(gzipHandler);
                } else {
                    peerServer.setHandler(peerHandler);
                }
                peerServer.setStopAtShutdown(true);
                threadPool.runBeforeStart(() -> {
                    try {
                        peerServer.start();
                        logger.info("Started peer networking server at {}:{}", host, port);
                    } catch (Exception e) {
                        logger.error("Failed to start peer networking server", e);
                        throw new RuntimeException(e.toString(), e);
                    }
                }, true);
            } else {
                peerServer = null;
                gateway = null;
                port = null;
                logger.info("shareMyAddress is disabled, will not start peer networking server");
            }
        }

        private Init() {
        }

    }

    private static final Runnable peerUnBlacklistingThread = () -> {
        try {

            long curTime = System.currentTimeMillis();
            for (Peer peer : peers.values()) {
                peer.updateBlacklistedStatus(curTime);
            }

        } catch (Exception e) {
            logger.debug("Error un-blacklisting peer", e);
        }
    };

    private static final Runnable peerConnectingThread = new Runnable() {
        private int getNumberOfConnectedPublicPeers() {
            int numberOfConnectedPeers = 0;
            for (Peer peer : peers.values()) {
                if (peer.getState() == Peer.State.CONNECTED) {
                    numberOfConnectedPeers++;
                }
            }
            return numberOfConnectedPeers;
        }

        @Override
        public void run() {
            try {
                int numConnectedPeers = getNumberOfConnectedPublicPeers();
                /*
                 * aggressive connection with while loop.
                 * if we have connected to our target amount we can exit loop.
                 * if peers size is equal or below connected value we have nothing to connect to
                 */
                while (!Thread.currentThread().isInterrupted() && ThreadPool.running.get()
                        && numConnectedPeers < maxNumberOfConnectedPublicPeers && peers.size() > numConnectedPeers) {
                    Peer peer = getAnyPeer(ThreadLocalRandom.current().nextInt(2) == 0 ? Peer.State.NON_CONNECTED
                            : Peer.State.DISCONNECTED);
                    if (peer != null) {
                        peer.connect(timeService.getEpochTime());
                        /*
                         * remove non connected peer. if peer is blacklisted, keep it to maintain
                         * blacklist time.
                         * Peers should never be removed if total peers are below our target to prevent
                         * total erase of peers
                         * if we loose Internet connection
                         */

                        if (!peer.isHigherOrEqualVersionThan(
                                Signum.getFluxCapacitor().getValue(FluxValues.MIN_PEER_VERSION))
                                || (peer.getNetworkName() != null
                                        && !peer.getNetworkName().equals(propertyService.getString(Props.NETWORK_NAME)))
                                || (peer.getState() != Peer.State.CONNECTED && !peer.isBlacklisted()
                                        && peers.size() > maxNumberOfConnectedPublicPeers)) {
                            removePeer(peer);
                        } else {
                            numConnectedPeers++;
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                int now = timeService.getEpochTime();
                for (Peer peer : peers.values()) {
                    if (peer.getState() == Peer.State.CONNECTED && now - peer.getLastUpdated() > 3600) {
                        peer.connect(timeService.getEpochTime());
                        if (!peer.isHigherOrEqualVersionThan(
                                Signum.getFluxCapacitor().getValue(FluxValues.MIN_PEER_VERSION)) ||
                                (peer.getNetworkName() != null && !peer.getNetworkName()
                                        .equals(Signum.getPropertyService().getString(Props.NETWORK_NAME)))
                                ||
                                (peer.getState() != Peer.State.CONNECTED && !peer.isBlacklisted()
                                        && peers.size() > maxNumberOfConnectedPublicPeers)) {
                            removePeer(peer);
                        }
                    }
                }

                if (lastSavedPeers != peers.size()) {
                    lastSavedPeers = peers.size();
                    updateSavedPeers();
                }

            } catch (Exception e) {
                logger.debug("Error connecting to peer", e);
            }
        }

        private void updateSavedPeers() {
            Set<String> oldPeers = new HashSet<>(Signum.getDbs().getPeerDb().loadPeers());
            Set<String> currentPeers = new HashSet<>();
            for (Peer peer : Peers.peers.values()) {
                if (peer.getAnnouncedAddress() != null
                        && !peer.isBlacklisted()
                        && !peer.isWellKnown()
                        && (peer.getNetworkName() == null || peer.getNetworkName()
                                .equals(Signum.getPropertyService().getString(Props.NETWORK_NAME)))
                        && peer.isHigherOrEqualVersionThan(
                                Signum.getFluxCapacitor().getValue(FluxValues.MIN_PEER_VERSION))) {
                    currentPeers.add(peer.getAnnouncedAddress());
                }
            }
            Set<String> toDelete = new HashSet<>(oldPeers);
            toDelete.removeAll(currentPeers);
            try {
                Signum.getStores().beginTransaction();
                Signum.getDbs().getPeerDb().deletePeers(toDelete);
                currentPeers.removeAll(oldPeers);
                Signum.getDbs().getPeerDb().addPeers(currentPeers);
                Signum.getStores().commitTransaction();
            } catch (Exception e) {
                Signum.getStores().rollbackTransaction();
                throw e;
            } finally {
                Signum.getStores().endTransaction();
            }
        }

    };

    private static final Runnable getMorePeersThread = new Runnable() {

        private final JsonElement getPeersRequest;
        {
            JsonObject request = new JsonObject();
            request.addProperty("requestType", "getPeers");
            getPeersRequest = prepareRequest(request);
        }

        private final AtomicBoolean addedNewPeer = new AtomicBoolean(false);
        {
            addListener(peer -> addedNewPeer.set(true), Event.NEW_PEER);
        }

        private boolean addListener(Listener<Peer> listener, Event eventType) {
            return Peers.listeners.addListener(listener, eventType);
        }

        @Override
        public void run() {
            try {
                /*
                 * We do not want more peers if above Threshold but we need enough to
                 * connect to selected number of peers
                 */
                if (peers.size() >= getMorePeersThreshold && peers.size() > maxNumberOfConnectedPublicPeers) {
                    return;
                }

                Peer peer = getAnyPeer(Peer.State.CONNECTED);
                if (peer == null) {
                    return;
                }
                JsonObject response = peer.send(getPeersRequest);
                if (response == null) {
                    return;
                }
                JsonArray peersJson = JSON.getAsJsonArray(response.get("peers"));
                Set<String> addedAddresses = new HashSet<>();
                if (peersJson != null) {
                    for (JsonElement announcedAddress : peersJson) {
                        if (addPeer(JSON.getAsString(announcedAddress)) != null) {
                            addedAddresses.add(JSON.getAsString(announcedAddress));
                        }
                    }
                    if (savePeers && addedNewPeer.get()) {
                        addedNewPeer.set(false);
                    }
                }

                JsonArray myPeers = new JsonArray();
                for (Peer myPeer : Peers.getAllPeers()) {
                    if (!myPeer.isBlacklisted() && myPeer.getAnnouncedAddress() != null
                            && myPeer.getState() == Peer.State.CONNECTED && myPeer.shareAddress()
                            && !addedAddresses.contains(myPeer.getAnnouncedAddress())
                            && !myPeer.getAnnouncedAddress().equals(peer.getAnnouncedAddress())
                            && (peer.getNetworkName() == null
                                    || peer.getNetworkName().equals(propertyService.getString(Props.NETWORK_NAME)))
                            && myPeer.isHigherOrEqualVersionThan(
                                    Signum.getFluxCapacitor().getValue(FluxValues.MIN_PEER_VERSION))) {
                        myPeers.add(myPeer.getAnnouncedAddress());
                    }
                }
                // executor shutdown?
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                if (myPeers.size() > 0) {
                    JsonObject request = new JsonObject();
                    request.addProperty("requestType", "addPeers");
                    request.add("peers", myPeers);
                    peer.send(prepareRequest(request));
                }

            } catch (Exception e) {
                logger.debug("Error requesting peers from a peer", e);
            }
        }
    };

    public static void shutdown(ThreadPool threadPool) {
        if (Init.peerServer != null) {
            try {
                Init.peerServer.stop();
            } catch (Exception e) {
                logger.info("Failed to stop peer server", e);
            }
        }
        if (Init.gateway != null) {
            try {
                Init.gateway.deletePortMapping(Init.port, "TCP");
            } catch (Exception e) {
                logger.info("Failed to remove UPNP rule from gateway", e);
            }
        }
        if (dumpPeersVersion != null) {
            StringBuilder buf = new StringBuilder();
            for (Map.Entry<String, String> entry : announcedAddresses.entrySet()) {
                Peer peer = peers.get(entry.getValue());
                if (peer != null && peer.getState() == Peer.State.CONNECTED && peer.shareAddress()
                        && !peer.isBlacklisted()
                        && peer.getVersion() != null
                        && peer.getVersion().toString().startsWith(dumpPeersVersion)) {
                    buf.append("('").append(entry.getKey()).append("'), ");
                }
            }
            if (logger.isInfoEnabled()) {
                logger.info(buf.toString());
            }
        }

        threadPool.shutdownExecutor(sendBlocksToPeersService);
    }

    public static boolean removeListener(Listener<Peer> listener, Event eventType) {
        return Peers.listeners.removeListener(listener, eventType);
    }

    static void notifyListeners(Peer peer, Event eventType) {
        Peers.listeners.notify(peer, eventType);
    }

    public static Collection<Peer> getAllPeers() {
        return allPeers;
    }

    public static List<Peer> getActivePeers() {
        List<Peer> activePeers = new ArrayList<>();
        for (Peer peer : peers.values()) {
            if (peer.getState() != Peer.State.NON_CONNECTED) {
                activePeers.add(peer);
            }
        }
        return activePeers;
    }

    public static Collection<Peer> getPeers(Peer.State state) {
        List<Peer> peerList = new ArrayList<>();
        for (Peer peer : peers.values()) {
            if (peer.getState() == state) {
                peerList.add(peer);
            }
        }
        return peerList;
    }

    public static Peer getPeer(String peerAddress) {
        return peers.get(peerAddress);
    }

    public static Peer addPeer(String announcedAddress) {
        if (announcedAddress == null) {
            return null;
        }
        announcedAddress = announcedAddress.trim();
        Peer peer;
        if ((peer = peers.get(announcedAddress)) != null) {
            return peer;
        }
        String address;
        if ((address = announcedAddresses.get(announcedAddress)) != null && (peer = peers.get(address)) != null) {
            return peer;
        }
        try {
            URI uri = new URI("http://" + announcedAddress);
            String host = uri.getHost();
            if ((peer = peers.get(host)) != null) {
                return peer;
            }
            InetAddress inetAddress = InetAddress.getByName(host);
            return addPeer(inetAddress.getHostAddress(), announcedAddress);
        } catch (URISyntaxException | UnknownHostException e) {
            return null;
        }
    }

    static Peer addPeer(final String address, final String announcedAddress) {
        // re-add the [] to ipv6 addresses lost in getHostAddress() above
        String cleanAddress = address;
        if (cleanAddress.split(":").length > 2) {
            cleanAddress = "[" + cleanAddress + "]";
        }
        Peer peer;
        if ((peer = peers.get(cleanAddress)) != null) {
            return peer;
        }
        String peerAddress = normalizeHostAndPort(cleanAddress);
        if (peerAddress == null) {
            return null;
        }
        if ((peer = peers.get(peerAddress)) != null) {
            return peer;
        }

        String announcedPeerAddress = address.equals(announcedAddress) ? peerAddress
                : normalizeHostAndPort(announcedAddress);

        if (Peers.myAddress != null && !Peers.myAddress.isEmpty()
                && Peers.myAddress.equalsIgnoreCase(announcedPeerAddress)) {
            return null;
        }

        peer = new PeerImpl(peerAddress, announcedPeerAddress);
        peers.put(peerAddress, peer);
        if (announcedAddress != null) {
            updateAddress(peer);
        }
        listeners.notify(peer, Event.NEW_PEER);
        return peer;
    }

    static Peer removePeer(Peer peer) {
        if (peer.getAnnouncedAddress() != null) {
            announcedAddresses.remove(peer.getAnnouncedAddress());
        }
        return peers.remove(peer.getPeerAddress());
    }

    static void updateAddress(Peer peer) {
        String oldAddress = announcedAddresses.put(peer.getAnnouncedAddress(), peer.getPeerAddress());
        if (oldAddress != null && !peer.getPeerAddress().equals(oldAddress)) {
            Peer oldPeer = peers.remove(oldAddress);
            if (oldPeer != null) {
                Peers.notifyListeners(oldPeer, Peers.Event.REMOVE);
            }
        }
    }

    public static void sendToSomePeers(Block block) {
        JsonObject request = block.getJsonObject();
        request.addProperty("requestType", "processBlock");

        blocksSendingService.submit(() -> {
            final JsonElement jsonRequest = prepareRequest(request);

            int successful = 0;
            List<Future<JsonObject>> expectedResponses = new ArrayList<>();
            for (final Peer peer : peers.values()) {

                if (peerEligibleForSending(peer, false)) {
                    Future<JsonObject> futureResponse = sendBlocksToPeersService.submit(() -> peer.send(jsonRequest));
                    expectedResponses.add(futureResponse);
                }
                if (expectedResponses.size() >= Peers.sendToPeersLimit - successful) {
                    for (Future<JsonObject> future : expectedResponses) {
                        try {
                            JsonObject response = future.get();
                            if (response != null && response.get("error") == null) {
                                successful += 1;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (ExecutionException e) {
                            logger.debug("Error in sendToSomePeers", e);
                        }

                    }
                    expectedResponses.clear();
                }
                if (successful >= Peers.sendToPeersLimit) {
                    return;
                }
            }
        });
    }

    private static final JsonElement getUnconfirmedTransactionsRequest;
    static {
        JsonObject request = new JsonObject();
        request.addProperty("requestType", "getUnconfirmedTransactions");
        getUnconfirmedTransactionsRequest = prepareRequest(request);
    }

    private static final ExecutorService utReceivingService = Executors.newCachedThreadPool();

    public static CompletableFuture<JsonObject> readUnconfirmedTransactionsNonBlocking(Peer peer) {
        return CompletableFuture.supplyAsync(() -> peer.send(getUnconfirmedTransactionsRequest), utReceivingService);
    }

    private static final ExecutorService utSendingService = Executors.newCachedThreadPool();

    private static final List<Peer> processingQueue = new ArrayList<>();
    private static final List<Peer> beingProcessed = new ArrayList<>();

    public static synchronized void feedingTime(Peer peer, Function<Peer, List<Transaction>> foodDispenser,
            BiConsumer<Peer, List<Transaction>> doneFeedingLog) {
        if (!beingProcessed.contains(peer)) {
            beingProcessed.add(peer);
            CompletableFuture.runAsync(() -> feedPeer(peer, foodDispenser, doneFeedingLog), utSendingService);
        } else if (!processingQueue.contains(peer)) {
            processingQueue.add(peer);
        }
    }

    private static void feedPeer(Peer peer, Function<Peer, List<Transaction>> foodDispenser,
            BiConsumer<Peer, List<Transaction>> doneFeedingLog) {
        List<Transaction> transactionsToSend = foodDispenser.apply(peer);

        if (!transactionsToSend.isEmpty()) {
            logger.trace("Feeding {} {} transactions", peer.getPeerAddress(), transactionsToSend.size());
            JsonObject response = peer.send(sendUnconfirmedTransactionsRequest(transactionsToSend));

            if (response != null && response.get("error") == null) {
                doneFeedingLog.accept(peer, transactionsToSend);
            } else {
                if (logger.isDebugEnabled())
                    logger.debug("Error feeding {} transactions: {} error: {}", peer.getPeerAddress(),
                            transactionsToSend.stream().map(Transaction::getId).collect(Collectors.toList()), response);
            }
        } else {
            logger.trace("No need to feed {}", peer.getPeerAddress());
        }

        beingProcessed.remove(peer);

        if (processingQueue.contains(peer)) {
            processingQueue.remove(peer);
            beingProcessed.add(peer);
            feedPeer(peer, foodDispenser, doneFeedingLog);
        }
    }

    private static JsonElement sendUnconfirmedTransactionsRequest(List<Transaction> transactions) {
        JsonObject request = new JsonObject();
        JsonArray transactionsData = new JsonArray();

        for (Transaction transaction : transactions) {
            transactionsData.add(transaction.getJsonObject());
        }

        request.addProperty("requestType", "processTransactions");
        request.add("transactions", transactionsData);

        return prepareRequest(request);
    }

    private static boolean peerEligibleForSending(Peer peer, boolean sendSameBRSclass) {
        return peer.isHigherOrEqualVersionThan(Signum.getFluxCapacitor().getValue(FluxValues.MIN_PEER_VERSION))
                && (peer.getNetworkName() == null
                        || peer.getNetworkName().equals(propertyService.getString(Props.NETWORK_NAME)))
                && (!sendSameBRSclass || peer.isAtLeastMyVersion())
                && !peer.isBlacklisted()
                && peer.getState() == Peer.State.CONNECTED
                && peer.getAnnouncedAddress() != null;
    }

    public static Peer getAnyPeer(Peer.State state) {
        if (!connectWellKnownFinished) {
            int wellKnownConnected = 0;
            for (Peer peer : peers.values()) {
                if (peer.isWellKnown() && peer.getState() == Peer.State.CONNECTED) {
                    wellKnownConnected++;
                }
            }
            if (wellKnownConnected >= connectWellKnownFirst) {
                connectWellKnownFinished = true;
                logger.info("Finished connecting to {} well known peers.", connectWellKnownFirst);
            }
        }

        if (peers.size() == 0) {
            // add back the well known peers in case we have been offline too long
            for (String wellKnownPeer : wellKnownPeers) {
                logger.debug("Adding well known peers {} back, we ended with no one", wellKnownPeer);
                addPeer(wellKnownPeer);
            }
        }

        List<Peer> selectedPeers = new ArrayList<>();
        for (Peer peer : peers.values()) {
            if (!peer.isBlacklisted() && peer.getState() == state && peer.shareAddress()
                    && (connectWellKnownFinished || peer.getState() == Peer.State.CONNECTED || peer.isWellKnown())) {
                selectedPeers.add(peer);
            }
        }

        if (!selectedPeers.isEmpty()) {
            return selectedPeers.get(r.nextInt(selectedPeers.size()));
        }
        return null;
    }

    public static List<Peer> getAllActivePriorityPlusSomeExtraPeers() {
        final List<Peer> peersActivePriorityPlusSomeExtraPeers = new ArrayList<>();
        int amountExtrasLeft = propertyService.getInt(P2P_SEND_TO_LIMIT);

        for (Peer peer : peers.values()) {
            if (peerEligibleForSending(peer, true)) {
                if (peer.isRebroadcastTarget()) {
                    peersActivePriorityPlusSomeExtraPeers.add(peer);
                } else if (amountExtrasLeft > 0) {
                    peersActivePriorityPlusSomeExtraPeers.add(peer);
                    amountExtrasLeft--;
                }
            }
        }

        return peersActivePriorityPlusSomeExtraPeers;
    }

    static String normalizeHostAndPort(String address) {
        try {
            if (address == null) {
                return null;
            }
            URI uri = new URI("http://" + address.trim());
            String host = uri.getHost();
            if (host == null || host.equals("")) {
                return null;
            }
            InetAddress inetAddress = InetAddress.getByName(host);
            if (inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() ||
                    inetAddress.isLinkLocalAddress()) {
                return null;
            }
            int port = uri.getPort();
            return port == -1 ? host : host + ':' + port;
        } catch (URISyntaxException | UnknownHostException e) {
            return null;
        }
    }

    private Peers() {
    } // never

}
