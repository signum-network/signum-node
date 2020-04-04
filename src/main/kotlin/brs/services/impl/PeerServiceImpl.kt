package brs.services.impl

import brs.Burst
import brs.api.grpc.proto.PeerApi
import brs.db.transaction
import brs.entity.Block
import brs.entity.DependencyProvider
import brs.entity.Transaction
import brs.objects.Constants.MIN_VERSION
import brs.objects.Props
import brs.objects.Props.P2P_ENABLE_TX_REBROADCAST
import brs.objects.Props.P2P_SEND_TO_LIMIT
import brs.peer.*
import brs.peer.Peer.Companion.isHigherOrEqualVersion
import brs.services.PeerService
import brs.services.RepeatingTask
import brs.services.Task
import brs.services.TaskType
import brs.util.Listeners
import brs.util.Version
import brs.util.delegates.Atomic
import brs.util.json.JSON.prepareRequest
import brs.util.json.toJsonString
import brs.util.logging.*
import brs.util.sync.Mutex
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.bitlet.weupnp.GatewayDevice
import org.bitlet.weupnp.GatewayDiscover
import org.bitlet.weupnp.PortMappingEntry
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.servlet.FilterMapping
import org.eclipse.jetty.servlet.ServletHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.servlets.DoSFilter
import org.slf4j.LoggerFactory
import org.xml.sax.SAXException
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.ThreadLocalRandom

class PeerServiceImpl(private val dp: DependencyProvider) : PeerService {
    private val random = Random()

    override val rebroadcastPeers: Set<PeerAddress>
    override val wellKnownPeers: Set<PeerAddress>

    init {
        val wellKnownPeersList = dp.propertyService.get(if (dp.propertyService.get(Props.DEV_TESTNET)) Props.DEV_P2P_BOOTSTRAP_PEERS else Props.P2P_BOOTSTRAP_PEERS)
            .mapNotNull { PeerAddress.parse(dp, it) }
            .toMutableSet()
        if (dp.propertyService.get(P2P_ENABLE_TX_REBROADCAST)) {
            rebroadcastPeers = dp.propertyService.get(if (dp.propertyService.get(Props.DEV_TESTNET)) Props.DEV_P2P_REBROADCAST_TO else Props.P2P_REBROADCAST_TO)
                    .mapNotNull { PeerAddress.parse(dp, it) }
                    .toSet()

            for (rePeer in rebroadcastPeers) {
                if (!wellKnownPeersList.contains(rePeer)) {
                    wellKnownPeersList.add(rePeer)
                }
            }
        } else {
            rebroadcastPeers = emptySet()
        }
        wellKnownPeers =
            if (wellKnownPeersList.isEmpty() || dp.propertyService.get(Props.DEV_OFFLINE)) emptySet() else wellKnownPeersList
    }

    override val knownBlacklistedPeers: Set<PeerAddress>

    private var peerServer: Server? = null
    private var gateway: GatewayDevice? = null
    private var httpPort: Int = -1

    private var connectWellKnownFirst: Int = 0
    private var connectWellKnownFinished: Boolean = false

    override val connectTimeout: Int
    override val readTimeout: Int
    override val blacklistingPeriod: Int
    override val getMorePeers = dp.propertyService.get(Props.P2P_GET_MORE_PEERS)

    override val myPlatform = dp.propertyService.get(Props.P2P_MY_PLATFORM)
    override val myAddress: String
    override val announcedAddress: PeerAddress?
    override val shareMyAddress: Boolean
    private val myHttpPeerServerPort: Int
    private val myGrpcPeerServerPort: Int
    private val useUpnp: Boolean
    private val maxNumberOfConnectedPublicPeers: Int
    private val sendToPeersLimit: Int
    private val usePeersDb: Boolean
    private val savePeers: Boolean
    private val getMorePeersThreshold: Int
    private var lastSavedPeers: Int = 0

    override val myJsonPeerInfoRequest: JsonElement
    override val myProtoPeerInfo: PeerApi.PeerInfo

    private val listeners = Listeners<Peer, PeerService.Event>()

    /**
     * All peers, identified by their actual remote address for use when a peer contacts us
     */
    private val peers = ConcurrentHashMap<String, Peer>()
    private val remoteAddressCache = ConcurrentHashMap<PeerAddress, String>()

    override val allPeers: Collection<Peer> get() = peers.values

    init {
        val configuredAddress = dp.propertyService.get(Props.P2P_MY_ADDRESS)
        myAddress = if (gateway != null && configuredAddress.isBlank()) {
            try {
                gateway!!.externalIPAddress
            } catch (e: Exception) {
                logger.safeInfo { "Can't get Gateway's IP address" }
                configuredAddress
            }
        } else configuredAddress

        myHttpPeerServerPort = dp.propertyService.get(Props.P2P_PORT)
        myGrpcPeerServerPort = dp.propertyService.get(Props.P2P_V2_PORT)
        useUpnp = dp.propertyService.get(Props.P2P_UPNP)
        shareMyAddress = dp.propertyService.get(Props.P2P_SHARE_MY_ADDRESS) && !dp.propertyService.get(Props.DEV_OFFLINE)

        val json = JsonObject()
        this.announcedAddress = PeerAddress.parse(dp, myAddress.trim(), defaultProtocol = PeerAddress.Protocol.GRPC)

        if (announcedAddress != null) {
            json.addProperty("announcedAddress", announcedAddress.toString())
        }
        json.addProperty("application", Burst.APPLICATION)
        json.addProperty("version", Burst.VERSION.toString())
        json.addProperty("platform", this.myPlatform)
        json.addProperty("shareAddress", this.shareMyAddress)
        logger.safeDebug { "My peer info: ${json.toJsonString()}" }
        json.addProperty("requestType", "getInfo")
        myJsonPeerInfoRequest = prepareRequest(json)
        myProtoPeerInfo = PeerApi.PeerInfo.newBuilder()
            .setApplication(Burst.APPLICATION)
            .setVersion(Burst.VERSION.toString())
            .setPlatform(this.myPlatform)
            .setShareAddress(this.shareMyAddress)
            .setAnnouncedAddress(announcedAddress?.toString() ?: "")
            .build()

        connectWellKnownFirst = dp.propertyService.get(Props.P2P_NUM_BOOTSTRAP_CONNECTIONS)
        connectWellKnownFinished = connectWellKnownFirst == 0

        val knownBlacklistedPeersList = dp.propertyService.get(Props.P2P_BLACKLISTED_PEERS)
            .mapNotNull { PeerAddress.parse(dp, it) }
        knownBlacklistedPeers = if (knownBlacklistedPeersList.isEmpty()) {
            emptySet()
        } else {
            knownBlacklistedPeersList.toSet()
        }

        maxNumberOfConnectedPublicPeers = dp.propertyService.get(Props.P2P_MAX_CONNECTIONS)
        connectTimeout = dp.propertyService.get(Props.P2P_TIMEOUT_CONNECT_MS)
        readTimeout = dp.propertyService.get(Props.P2P_TIMEOUT_READ_MS)

        blacklistingPeriod = dp.propertyService.get(Props.P2P_BLACKLISTING_TIME_MS)
        sendToPeersLimit = dp.propertyService.get(P2P_SEND_TO_LIMIT)
        usePeersDb = dp.propertyService.get(Props.P2P_USE_PEERS_DB) && !dp.propertyService.get(Props.DEV_OFFLINE)
        savePeers = usePeersDb && dp.propertyService.get(Props.P2P_SAVE_PEERS)
        getMorePeersThreshold = dp.propertyService.get(Props.P2P_GET_MORE_PEERS_THRESHOLD)

        httpPort = if (dp.propertyService.get(Props.DEV_TESTNET)) 7123 else myHttpPeerServerPort
        if (shareMyAddress) {
            if (useUpnp) {
                val gatewayDiscover = GatewayDiscover()
                gatewayDiscover.timeout = 2000
                try {
                    gatewayDiscover.discover()
                } catch (ignored: Exception) {
                }

                logger.safeTrace { "Looking for Gateway Devices" }
                if (gatewayDiscover.validGateway != null) {
                    gateway = gatewayDiscover.validGateway
                }

                val gwDiscover: Task = {
                    if (gateway != null) {
                        GatewayDevice.setHttpReadTimeout(2000)
                        try {
                            val localAddress = gateway!!.localAddress
                            val externalIPAddress = gateway!!.externalIPAddress
                            logger.safeInfo { "Attempting to map $externalIPAddress:$httpPort -> $localAddress:$httpPort on Gateway ${gateway!!.modelName} (${gateway!!.modelDescription})" }
                            when {
                                gateway!!.getSpecificPortMappingEntry(httpPort, "TCP", PortMappingEntry().apply { externalPort = httpPort; internalPort = httpPort }) -> logger.safeInfo { "Port was already mapped. Aborting test." }
                                gateway!!.addPortMapping(httpPort, httpPort, localAddress.hostAddress, "TCP", "burstcoin") -> logger.safeInfo { "UPnP Mapping successful" }
                                else -> logger.safeWarn { "UPnP Mapping was denied!" }
                            }
                        } catch (e: IOException) {
                            logger.safeError(e) { "Can't start UPnP" }
                        } catch (e: SAXException) {
                            logger.safeError(e) { "Can't start UPnP" }
                        }
                    }
                }
                if (this.gateway != null) {
                    dp.taskSchedulerService.runBeforeStart(gwDiscover)
                } else {
                    logger.safeWarn { "Tried to establish UPnP, but it was denied by the network." }
                }
            }

            peerServer = Server()
            val connector = ServerConnector(peerServer)
            connector.port = httpPort
            val host = dp.propertyService.get(Props.P2P_LISTEN)
            connector.host = host
            connector.idleTimeout = dp.propertyService.get(Props.P2P_TIMEOUT_IDLE_MS).toLong()
            connector.reuseAddress = true
            peerServer!!.addConnector(connector)

            val peerServletHolder = ServletHolder(PeerServlet(dp))
            val isGzipEnabled = dp.propertyService.get(Props.JETTY_P2P_GZIP_FILTER)
            peerServletHolder.setInitParameter("isGzipEnabled", isGzipEnabled.toString())

            val peerHandler = ServletHandler()
            peerHandler.addServletWithMapping(peerServletHolder, "/*")

            if (dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER)) {
                val dosFilterHolder =
                    peerHandler.addFilterWithMapping(DoSFilter::class.java, "/*", FilterMapping.DEFAULT)
                dosFilterHolder.setInitParameter(
                    "maxRequestsPerSec",
                    dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER_MAX_REQUESTS_PER_SEC)
                )
                dosFilterHolder.setInitParameter(
                    "throttledRequests",
                    dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER_THROTTLED_REQUESTS)
                )
                dosFilterHolder.setInitParameter("delayMs", dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER_DELAY_MS))
                dosFilterHolder.setInitParameter(
                    "maxWaitMs",
                    dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER_MAX_WAIT_MS)
                )
                dosFilterHolder.setInitParameter(
                    "maxRequestMs",
                    dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER_MAX_REQUEST_MS)
                )
                dosFilterHolder.setInitParameter(
                    "maxthrottleMs",
                    dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER_THROTTLE_MS)
                )
                dosFilterHolder.setInitParameter(
                    "maxIdleTrackerMs",
                    dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER_MAX_IDLE_TRACKER_MS)
                )
                dosFilterHolder.setInitParameter(
                    "trackSessions",
                    dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER_TRACK_SESSIONS)
                )
                dosFilterHolder.setInitParameter(
                    "insertHeaders",
                    dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER_INSERT_HEADERS)
                )
                dosFilterHolder.setInitParameter(
                    "remotePort",
                    dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER_REMOTE_PORT)
                )
                dosFilterHolder.setInitParameter(
                    "ipWhitelist",
                    dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER_IP_WHITELIST)
                )
                dosFilterHolder.setInitParameter(
                    "managedAttr",
                    dp.propertyService.get(Props.JETTY_P2P_DOS_FILTER_MANAGED_ATTR)
                )
                dosFilterHolder.isAsyncSupported = true
            }

            if (isGzipEnabled) {
                val gzipHandler = GzipHandler()
                gzipHandler.setIncludedMethods(dp.propertyService.get(Props.JETTY_P2P_GZIP_FILTER_METHODS))
                gzipHandler.inflateBufferSize = dp.propertyService.get(Props.JETTY_P2P_GZIP_FILTER_BUFFER_SIZE)
                gzipHandler.minGzipSize = dp.propertyService.get(Props.JETTY_P2P_GZIP_FILTER_MIN_GZIP_SIZE)
                gzipHandler.setIncludedMimeTypes("text/plain")
                gzipHandler.handler = peerHandler
                peerServer!!.handler = gzipHandler
            } else {
                peerServer!!.handler = peerHandler
            }
            peerServer!!.stopAtShutdown = true
            dp.taskSchedulerService.runBeforeStart {
                peerServer!!.start()
                logger.safeInfo { "Started peer networking server at $host:$httpPort" }
            }
        } else {
            logger.safeInfo { "shareMyAddress is disabled, will not start peer networking server" }
        }
    }

    private val peerUnBlacklistingThread: Task = {
        try {
            val curTime = System.currentTimeMillis()
            for (peer in peers.values) {
                peer.updateBlacklistedStatus(curTime)
            }
        } catch (e: Exception) {
            logger.safeDebug(e) { "Error un-blacklisting peer" }
        }
    }

    private val numberOfConnectedPublicPeers: Int
        get() {
            var numberOfConnectedPeers = 0
            for (peer in peers.values) {
                if (peer.state == Peer.State.CONNECTED) {
                    numberOfConnectedPeers++
                }
            }
            return numberOfConnectedPeers
        }

    private fun updateSavedPeers() {
        dp.db.transaction {
            dp.peerDb.updatePeers(peers.values
                .filter { peer -> !peer.isBlacklisted && !peer.isWellKnown && peer.isHigherOrEqualVersionThan(MIN_VERSION) }
                .map { it.address.toString() })
        }
    }

    private val peerConnectingThread: RepeatingTask = {
        run {
            try {
                var numConnectedPeers = numberOfConnectedPublicPeers
                /*
             * aggressive connection with while loop.
             * if we have connected to our target amount we can exit loop.
             * if peers size is equal or below connected value we have nothing to connect to
             */
                while (numConnectedPeers < maxNumberOfConnectedPublicPeers && peers.size > numConnectedPeers) {
                    val peer =
                        getAnyPeer(if (ThreadLocalRandom.current().nextInt(2) == 0) Peer.State.NON_CONNECTED else Peer.State.DISCONNECTED)
                    if (peer != null) {
                        if (!peer.connect()) return@run true
                        /*
                         * remove non connected peer. if peer is blacklisted, keep it to maintain blacklist time.
                         * Peers should never be removed if total peers are below our target to prevent total erase of peers
                         * if we loose Internet connection
                         */
                        if (!peer.isHigherOrEqualVersionThan(MIN_VERSION) || peer.state != Peer.State.CONNECTED && !peer.isBlacklisted && peers.size > maxNumberOfConnectedPublicPeers) {
                            removePeer(peer)
                        } else {
                            numConnectedPeers++
                        }
                    }
                    Thread.sleep(1000)
                }

                val now = dp.timeService.epochTime
                for (peer in peers.values) {
                    if (peer.state == Peer.State.CONNECTED && now - peer.lastUpdated > 3600 && (!peer.connect() || !peer.isHigherOrEqualVersionThan(MIN_VERSION) || peer.state != Peer.State.CONNECTED && !peer.isBlacklisted && peers.size > maxNumberOfConnectedPublicPeers)) {
                        removePeer(peer)
                    }
                }

                if (lastSavedPeers != peers.size) {
                    lastSavedPeers = peers.size
                    updateSavedPeers()
                }
            } catch (e: Exception) {
                logger.safeDebug(e) { "Error connecting to peer" }
            }
            return@run true
        }
    }

    private var addedNewPeer by Atomic(false)

    init {
        listeners.addListener(PeerService.Event.NEW_PEER) { addedNewPeer = true }
    }

    private val getMorePeersThread: RepeatingTask = {
        run {
            try {
                /* We do not want more peers if above Threshold but we need enough to
                 * connect to selected number of peers
                 */
                if (peers.size >= getMorePeersThreshold && peers.size > maxNumberOfConnectedPublicPeers) {
                    return@run false
                }

                val peer = getAnyPeer(Peer.State.CONNECTED) ?: return@run false
                val newAddresses = peer.getPeers() ?: return@run true
                if (!newAddresses.isEmpty()) {
                    for (announcedAddress in newAddresses) {
                        getOrAddPeer(announcedAddress)
                    }
                    if (savePeers && addedNewPeer) { // FIXME: Atomics do not guarantee exclusivity in this way
                        addedNewPeer = false
                    }
                }

                val myPeers = allPeers.filter { myPeer -> !myPeer.isBlacklisted
                        && myPeer.state == Peer.State.CONNECTED && myPeer.shareAddress
                        && !newAddresses.contains(myPeer.address)
                        && myPeer.address != peer.address
                        && myPeer.isHigherOrEqualVersionThan(MIN_VERSION) }
                    .map { it.address }

                if (myPeers.isNotEmpty()) {
                    peer.addPeers(myPeers)
                }
            } catch (e: Exception) {
                logger.safeDebug(e) { "Error requesting peers from a peer" }
            }
            return@run true
        }
    }

    init {
        dp.taskSchedulerService.runBeforeStart {
            if (wellKnownPeers.isNotEmpty()) {
                loadPeers(wellKnownPeers)
            }
            if (usePeersDb) {
                logger.safeDebug { "Loading known peers from the database..." }
                loadPeers(dp.peerDb.loadPeers().mapNotNull { PeerAddress.parse(dp, it) })
            }
            lastSavedPeers = peers.size
        }

        if (!dp.propertyService.get(Props.DEV_OFFLINE)) {
            dp.taskSchedulerService.scheduleTask(TaskType.IO, peerConnectingThread)
            dp.taskSchedulerService.scheduleTaskWithDelay(TaskType.IO, 0, 1000, peerUnBlacklistingThread)
            if (getMorePeers) {
                dp.taskSchedulerService.scheduleTask(TaskType.IO, getMorePeersThread)
            }
        }
    }

    override val activePeers: List<Peer>
        get() = peers.values.filter { it.state != Peer.State.NON_CONNECTED }

    private val processingQueue = mutableListOf<Peer>()
    private val beingProcessed = mutableListOf<Peer>()
    private val processingMutex = Mutex()

    override val allActivePriorityPlusSomeExtraPeers: MutableList<Peer>
        get() {
            val peersActivePriorityPlusSomeExtraPeers = mutableListOf<Peer>()
            var amountExtrasLeft = dp.propertyService.get(P2P_SEND_TO_LIMIT)

            for (peer in peers.values) {
                if (peerEligibleForSending(peer, true)) {
                    if (peer.isRebroadcastTarget) {
                        peersActivePriorityPlusSomeExtraPeers.add(peer)
                    } else if (amountExtrasLeft > 0) {
                        peersActivePriorityPlusSomeExtraPeers.add(peer)
                        amountExtrasLeft--
                    }
                }
            }

            return peersActivePriorityPlusSomeExtraPeers
        }

    override fun isSupportedUserAgent(header: String?): Boolean {
        return if (header == null || header.isEmpty() || !header.trim().startsWith("BRS/")) {
            false
        } else {
            try {
                isHigherOrEqualVersion(MIN_VERSION, Version.parse(header.trim().substring("BRS/".length)))
            } catch (e: IllegalArgumentException) {
                false
            }
        }
    }

    /**
     * Loads peers from a list of addresses
     */
    private fun loadPeers(addresses: Collection<PeerAddress>) {
        for (address in addresses) {
            getOrAddPeer(address)
        }
    }

    override fun shutdown() {
        if (peerServer != null) {
            try {
                peerServer!!.stop()
            } catch (e: Exception) {
                logger.safeInfo(e) { "Failed to stop peer server" }
            }
        }
        if (gateway != null) {
            try {
                gateway!!.deletePortMapping(httpPort, "TCP")
            } catch (e: Exception) {
                logger.safeInfo(e) { "Failed to remove UPNP rule from gateway" }
            }
        }
    }

    override fun notifyListeners(peer: Peer, eventType: PeerService.Event) {
        this.listeners.accept(eventType, peer)
    }

    override fun getPeers(state: Peer.State): Collection<Peer> {
        val peerList = mutableListOf<Peer>()
        for (peer in peers.values) {
            if (peer.state == state) {
                peerList.add(peer)
            }
        }
        return peerList
    }

    override fun getPeer(peerAddress: String): Peer? {
        return peers[peerAddress]
    }

    override fun getOrAddPeer(remoteAddress: String): Peer {
        val cleanRemoteAddress = remoteAddress.trim()
        var peer = peers[cleanRemoteAddress]
        if (peer != null) {
            return peer
        }
        peer = if (cleanRemoteAddress.startsWith("grpc://")) {
            GrpcPeerImpl(dp, remoteAddress, null)
        } else {
            HttpPeerImpl(dp, remoteAddress, null)
        }
        peers[cleanRemoteAddress] = peer
        listeners.accept(PeerService.Event.NEW_PEER, peer)
        return peer
    }

    override fun getOrAddPeer(address: PeerAddress): Peer {
        var remoteAddress = remoteAddressCache[address]
        if (remoteAddress != null) {
            val peer = peers[remoteAddress]
            if (peer != null) return peer
        }
        remoteAddress = address.toString()
        var peer = peers[remoteAddress]
        if (peer != null) return peer
        peer = when (address.protocol) {
            PeerAddress.Protocol.HTTP -> HttpPeerImpl(dp, remoteAddress, address)
            PeerAddress.Protocol.GRPC -> GrpcPeerImpl(dp, remoteAddress, address)
        }
        remoteAddressCache[address] = remoteAddress
        peers[remoteAddress] = peer
        listeners.accept(PeerService.Event.NEW_PEER, peer)
        return peer
    }

    override fun removePeer(peer: Peer) {
        peers.remove(peer.remoteAddress)
        notifyListeners(peer, PeerService.Event.REMOVE)
    }

    override fun updateAddress(peer: Peer) {
        val oldAddress = remoteAddressCache.put(peer.address, peer.remoteAddress)
        if (oldAddress != null && peer.remoteAddress != oldAddress) {
            val oldPeer = peers.remove(oldAddress)
            if (oldPeer != null) {
                this.notifyListeners(oldPeer, PeerService.Event.REMOVE)
            }
        }
    }

    override fun sendToSomePeers(block: Block) {
        dp.taskSchedulerService.run {
            var successful = 0
            val expectedResponses = mutableListOf<Future<Boolean?>>()
            for (peer in peers.values) {
                if (peerEligibleForSending(peer, false)) {
                    val deferred = dp.taskSchedulerService.async(TaskType.IO) { peer.sendBlock(block) }
                    expectedResponses.add(deferred)
                }
                if (expectedResponses.size >= sendToPeersLimit - successful) {
                    for (expectedResponse in expectedResponses) {
                        try {
                            val response = expectedResponse.get()
                            if (response == true) {
                                successful += 1
                            }
                        } catch (e: ExecutionException) {
                            logger.safeDebug(e) { "Error in sendToSomePeers" }
                        }
                    }
                    expectedResponses.clear()
                }
                if (successful >= sendToPeersLimit) {
                    return@run
                }
            }
        }
    }

    override fun feedingTime(
        peer: Peer,
        foodDispenser: (Peer) -> Collection<Transaction>,
        doneFeedingLog: (Peer, Collection<Transaction>) -> Unit
    ) {
        processingMutex.withLock<Unit> {
            when {
                !beingProcessed.contains(peer) -> {
                    beingProcessed.add(peer)
                    dp.taskSchedulerService.async(TaskType.IO) { feedPeer(peer, foodDispenser, doneFeedingLog) }
                }
                !processingQueue.contains(peer) -> processingQueue.add(peer)
            }
        }
    }

    private fun feedPeer(peer: Peer, foodDispenser: (Peer) -> Collection<Transaction>, doneFeedingLog: (Peer, Collection<Transaction>) -> Unit) {
        val transactionsToSend = foodDispenser(peer)

        if (transactionsToSend.isNotEmpty()) {
            logger.safeTrace { "Feeding ${peer.address} ${transactionsToSend.size} transactions" }
            peer.sendUnconfirmedTransactions(transactionsToSend)
        } else {
            logger.safeTrace { "No need to feed ${peer.address}" }
        }

        processingMutex.withLock {
            beingProcessed.remove(peer)

            if (processingQueue.contains(peer)) {
                processingQueue.remove(peer)
                beingProcessed.add(peer)
                feedPeer(peer, foodDispenser, doneFeedingLog)
            }
        }
    }

    private fun peerEligibleForSending(peer: Peer, sendSameBRSclass: Boolean): Boolean {
        return (peer.isHigherOrEqualVersionThan(MIN_VERSION)
                && (!sendSameBRSclass || peer.isAtLeastMyVersion)
                && !peer.isBlacklisted
                && peer.state == Peer.State.CONNECTED)
    }

    override fun getAnyPeer(state: Peer.State): Peer? {
        if (!connectWellKnownFinished) {
            var wellKnownConnected = 0
            for (peer in peers.values) {
                if (peer.isWellKnown && peer.state == Peer.State.CONNECTED) {
                    wellKnownConnected++
                }
            }
            if (wellKnownConnected >= connectWellKnownFirst) {
                connectWellKnownFinished = true
                logger.safeInfo { "Finished connecting to $connectWellKnownFirst well known peers." }
                val webSchema = if (dp.propertyService.get(Props.API_SSL)) "https" else "http"
                val webHost = dp.propertyService.get(Props.API_LISTEN)
                val webPort = dp.propertyService.get(if (dp.propertyService.get(Props.DEV_TESTNET)) Props.DEV_API_PORT else Props.API_PORT)
                logger.safeInfo { "You can open your Burst Wallet in your favorite browser with: $webSchema://$webHost:$webPort" }
            }
        }

        val selectedPeers = peers.values.filter { peer -> !peer.isBlacklisted && peer.state == state && peer.shareAddress && (connectWellKnownFinished || peer.state == Peer.State.CONNECTED || peer.isWellKnown) }
        return if (selectedPeers.isNotEmpty()) selectedPeers[random.nextInt(selectedPeers.size)] else null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PeerServiceImpl::class.java)
    }
}
