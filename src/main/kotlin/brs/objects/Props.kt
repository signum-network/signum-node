package brs.objects

import brs.entity.Prop

object Props {
    // DEV options
    val DEV_OFFLINE = Prop("DEV.Offline", false)
    val DEV_TESTNET = Prop("DEV.TestNet", false)
    val DEV_API_PORT = Prop("DEV.API.Port", 6876)
    val DEV_API_V2_PORT = Prop("DEV.API.V2.Port", 6878)

    val DEV_MOCK_MINING = Prop("DEV.mockMining", false)
    val DEV_MOCK_MINING_DEADLINE = Prop("DEV.mockMining.deadline", 10)

    val DEV_DB_URL = Prop("DEV.DB.Url", "")
    val DEV_DB_USERNAME = Prop("DEV.DB.Username", "")
    val DEV_DB_PASSWORD = Prop("DEV.DB.Password", "")

    val DEV_P2P_REBROADCAST_TO = Prop("DEV.P2P.rebroadcastTo", emptyList<String>())
    val DEV_P2P_BOOTSTRAP_PEERS = Prop("DEV.P2P.BootstrapPeers", emptyList<String>())

    val DEV_REWARD_RECIPIENT_ENABLE_BLOCK_HEIGHT = Prop("DEV.rewardRecipient.startBlock", -1)
    val DEV_DIGITAL_GOODS_STORE_BLOCK_HEIGHT = Prop("DEV.digitalGoodsStore.startBlock", -1)
    val DEV_AUTOMATED_TRANSACTION_BLOCK_HEIGHT = Prop("DEV.automatedTransactions.startBlock", -1)
    val DEV_AT_FIX_BLOCK_2_BLOCK_HEIGHT = Prop("DEV.atFixBlock2.startBlock", -1)
    val DEV_AT_FIX_BLOCK_3_BLOCK_HEIGHT = Prop("DEV.atFixBlock3.startBlock", -1)
    val DEV_AT_FIX_BLOCK_4_BLOCK_HEIGHT = Prop("DEV.atFixBlock4.startBlock", -1)
    val DEV_PRE_DYMAXION_BLOCK_HEIGHT = Prop("DEV.preDymaxion.startBlock", -1)
    val DEV_POC2_BLOCK_HEIGHT = Prop("DEV.poc2.startBlock", -1)
    val DEV_NEXT_FORK_BLOCK_HEIGHT = Prop("DEV.nextFork.startBlock", -1)
	
	// Checkpoint block for low CPU usage when syncing an empty database
	val BRS_CHECKPOINT_HEIGHT = Prop("brs.checkPointHeight", 730_000)
	val BRS_CHECKPOINT_HASH = Prop("brs.checkPointPrevHash", "7c9f8eb553ae1c47cb8960847f6ae672a9923d9c0c4c13a2e23a430e1099a5bc")
	val DEV_CHECKPOINT_HEIGHT = Prop("DEV.checkPointHeight", 150_000)
	val DEV_CHECKPOINT_HASH = Prop("DEV.checkPointPrevHash", "c99b807f4bff0d439375d083e2e04c465e96ec36d85092e8faba7b9a19534b94")

    // GPU options
    val GPU_ACCELERATION = Prop("GPU.Acceleration", false)
    val GPU_AUTODETECT = Prop("GPU.AutoDetect", true)
    val GPU_PLATFORM_IDX = Prop("GPU.PlatformIdx", 0)
    val GPU_DEVICE_IDX = Prop("GPU.DeviceIdx", 0)
    val GPU_UNVERIFIED_QUEUE = Prop("GPU.UnverifiedQueue", 1000)
    val GPU_HASHES_PER_BATCH = Prop("GPU.HashesPerBatch", 1000)
    val GPU_MEM_PERCENT = Prop("GPU.MemPercent", 50)

    // DB options
    val DB_URL = Prop("DB.Url", "jdbc:h2:file:./burst;DB_CLOSE_ON_EXIT=FALSE")
    val DB_USERNAME = Prop("DB.Username", "")
    val DB_PASSWORD = Prop("DB.Password", "")
    val DB_CONNECTIONS = Prop("DB.Connections", 30)

    val DB_TRIM_DERIVED_TABLES = Prop("DB.trimDerivedTables", true)
    val DB_MAX_ROLLBACK = Prop("DB.maxRollback", 1440)

    val BRS_TEST_UNCONFIRMED_TRANSACTIONS = Prop("brs.testUnconfirmedTransactions", false)

    val DB_H2_DEFRAG_ON_SHUTDOWN = Prop("Db.H2.DefragOnShutdown", false)

    val BRS_BLOCK_CACHE_MB = Prop("brs.blockCacheMB", 40)

    // P2P options

    val P2P_MY_PLATFORM = Prop("P2P.myPlatform", "PC")
    val P2P_MY_ADDRESS = Prop("P2P.myAddress", "")
    val P2P_LISTEN = Prop("P2P.Listen", "0.0.0.0")
    val P2P_PORT = Prop("P2P.Port", 8123)
    val P2P_UPNP = Prop("P2P.UPnP", true)
    val P2P_SHARE_MY_ADDRESS = Prop("P2P.shareMyAddress", true)
    val P2P_ENABLE_TX_REBROADCAST = Prop("P2P.enableTxRebroadcast", true)
    val P2P_REBROADCAST_TO = Prop("P2P.rebroadcastTo", emptyList<String>())
    val P2P_BOOTSTRAP_PEERS = Prop("P2P.BootstrapPeers", emptyList<String>())
    val P2P_NUM_BOOTSTRAP_CONNECTIONS = Prop("P2P.NumBootstrapConnections", 4)
    val P2P_BLACKLISTED_PEERS = Prop("P2P.BlacklistedPeers", emptyList<String>())
    val P2P_MAX_CONNECTIONS = Prop("P2P.MaxConnections", 20)
    val P2P_TIMEOUT_CONNECT_MS = Prop("P2P.TimeoutConnect_ms", 4000)
    val P2P_TIMEOUT_READ_MS = Prop("P2P.TimeoutRead_ms", 8000)
    val P2P_BLACKLISTING_TIME_MS = Prop("P2P.BlacklistingTime_ms", 600000)

    val P2P_TIMEOUT_IDLE_MS = Prop("P2P.TimeoutIdle_ms", 30000)

    val P2P_USE_PEERS_DB = Prop("P2P.usePeersDb", true)
    val P2P_SAVE_PEERS = Prop("P2P.savePeers", true)
    val P2P_GET_MORE_PEERS = Prop("P2P.getMorePeers", true)
    val P2P_GET_MORE_PEERS_THRESHOLD = Prop("P2P.getMorePeersThreshold", 400)

    val P2P_SEND_TO_LIMIT = Prop("P2P.sendToLimit", 10)

    val P2P_MAX_UNCONFIRMED_TRANSACTIONS = Prop("P2P.maxUnconfirmedTransactions", 8192)
    val P2P_MAX_PERCENTAGE_UNCONFIRMED_TRANSACTIONS_FULL_HASH_REFERENCE =
        Prop("P2P.maxUnconfirmedTransactionsFullHashReferencePercentage", 5)

    val P2P_MAX_UNCONFIRMED_TRANSACTIONS_RAW_SIZE_BYTES_TO_SEND = Prop("P2P.maxUTRawSizeBytesToSend", 175000)

    // API options
    val API_DEBUG = Prop("API.Debug", false)
    val API_SSL = Prop("API.SSL", false)
    val API_SERVER = Prop("API.Server", true)
    val API_V2_SERVER = Prop("API.V2.Server", true)
    val API_ALLOWED = Prop("API.allowed", listOf("127.0.0.1", "localhost", "[0:0:0:0:0:0:0:1]"))

    val API_ACCEPT_SURPLUS_PARAMS = Prop("API.AcceptSurplusParams", false)

    val API_LISTEN = Prop("API.Listen", "127.0.0.1")
    val API_PORT = Prop("API.Port", 8125)
    val API_V2_LISTEN = Prop("API.V2.Listen", "0.0.0.0")
    val API_V2_PORT = Prop("API.V2.Port", 8121)

    val P2P_V2_LISTEN = Prop("P2P.V2.Listen", "0.0.0.0")
    val P2P_V2_PORT = Prop("P2P.V2.Port", 8120)
    val DEV_P2P_V2_PORT = Prop("DEV.P2P.V2.Port", 7120)

    val API_UI_DIR = Prop("API.UI_Dir", "html/ui")
    val API_SSL_KEY_STORE_PATH = Prop("API.SSL_keyStorePath", "keystore")
    val API_SSL_KEY_STORE_PASSWORD = Prop("API.SSL_keyStorePassword", "password")
    val API_SERVER_IDLE_TIMEOUT = Prop("API.ServerIdleTimeout", 30000)
    val API_SERVER_ENFORCE_POST = Prop("API.ServerEnforcePOST", true)
    val API_ALLOWED_ORIGINS = Prop("API.AllowedOrigins", "*")

    val JETTY_API_GZIP_FILTER = Prop("JETTY.API.GzipFilter", true)
    val JETTY_API_GZIP_FILTER_METHODS = Prop("JETTY.API.GZIPFilter.methods", "GET, POST")
    val JETTY_API_GZIP_FILTER_BUFFER_SIZE = Prop("JETTY.API.GZIPFilter.bufferSize", 8192)
    val JETTY_API_GZIP_FILTER_MIN_GZIP_SIZE = Prop("JETTY.API.GZIPFilter.minGzipSize", 0)

    val JETTY_API_DOS_FILTER = Prop("JETTY.API.DoSFilter", true)
    val JETTY_API_DOS_FILTER_MAX_REQUEST_PER_SEC = Prop("JETTY.API.DoSFilter.maxRequestsPerSec", "30")
    val JETTY_API_DOS_FILTER_THROTTLED_REQUESTS = Prop("JETTY.API.DoSFilter.throttledRequests", "5")
    val JETTY_API_DOS_FILTER_DELAY_MS = Prop("JETTY.API.DoSFilter.delayMs", "500")
    val JETTY_API_DOS_FILTER_MAX_WAIT_MS = Prop("JETTY.API.DoSFilter.maxWaitMs", "50")
    val JETTY_API_DOS_FILTER_MAX_REQUEST_MS = Prop("JETTY.API.DoSFilter.maxRequestMs", "30000")
    val JETTY_API_DOS_FILTER_THROTTLE_MS = Prop("JETTY.API.DoSFilter.throttleMs", "30000")
    val JETTY_API_DOS_FILTER_MAX_IDLE_TRACKER_MS = Prop("JETTY.API.DoSFilter.maxIdleTrackerMs", "30000")
    val JETTY_API_DOS_FILTER_TRACK_SESSIONS = Prop("JETTY.API.DoSFilter.trackSessions", "false")
    val JETTY_API_DOS_FILTER_INSERT_HEADERS = Prop("JETTY.API.DoSFilter.insertHeaders", "true")
    val JETTY_API_DOS_FILTER_REMOTE_PORT = Prop("JETTY.API.DoSFilter.remotePort", "false")
    val JETTY_API_DOS_FILTER_IP_WHITELIST = Prop("JETTY.API.DoSFilter.ipWhitelist", "")
    val JETTY_API_DOS_FILTER_MANAGED_ATTR = Prop("JETTY.API.DoSFilter.managedAttr", "true")

    val JETTY_P2P_GZIP_FILTER = Prop("JETTY.P2P.GZIPFilter", false)
    val JETTY_P2P_GZIP_FILTER_METHODS = Prop("JETTY.P2P.GZIPFilter.methods", "GET, POST")
    val JETTY_P2P_GZIP_FILTER_BUFFER_SIZE = Prop("JETTY.P2P.GZIPFilter.bufferSize", 8192)
    val JETTY_P2P_GZIP_FILTER_MIN_GZIP_SIZE = Prop("JETTY.P2P.GZIPFilter.minGzipSize", 0)

    val JETTY_P2P_DOS_FILTER = Prop("JETTY.P2P.DoSFilter", true)
    val JETTY_P2P_DOS_FILTER_MAX_REQUESTS_PER_SEC = Prop("JETTY.P2P.DoSFilter.maxRequestsPerSec", "30")
    val JETTY_P2P_DOS_FILTER_THROTTLED_REQUESTS = Prop("JETTY.P2P.DoSFilter.throttledRequests", "5")
    val JETTY_P2P_DOS_FILTER_DELAY_MS = Prop("JETTY.P2P.DoSFilter.delayMs", "500")
    val JETTY_P2P_DOS_FILTER_MAX_WAIT_MS = Prop("JETTY.P2P.DoSFilter.maxWaitMs", "50")
    val JETTY_P2P_DOS_FILTER_MAX_REQUEST_MS = Prop("JETTY.P2P.DoSFilter.maxRequestMs", "300000")
    val JETTY_P2P_DOS_FILTER_THROTTLE_MS = Prop("JETTY.P2P.DoSFilter.throttleMs", "30000")
    val JETTY_P2P_DOS_FILTER_MAX_IDLE_TRACKER_MS = Prop("JETTY.P2P.DoSFilter.maxIdleTrackerMs", "30000")
    val JETTY_P2P_DOS_FILTER_TRACK_SESSIONS = Prop("JETTY.P2P.DoSFilter.trackSessions", "false")
    val JETTY_P2P_DOS_FILTER_INSERT_HEADERS = Prop("JETTY.P2P.DoSFilter.insertHeaders", "true")
    val JETTY_P2P_DOS_FILTER_REMOTE_PORT = Prop("JETTY.P2P.DoSFilter.remotePort", "false")
    val JETTY_P2P_DOS_FILTER_IP_WHITELIST = Prop("JETTY.P2P.DoSFilter.ipWhitelist", "")
    val JETTY_P2P_DOS_FILTER_MANAGED_ATTR = Prop("JETTY.P2P.DoSFilter.managedAttr", "true")

    val AUTO_POP_OFF_ENABLED = Prop("AutoPopOff.Enable", true)

    val ENABLE_AT_DEBUG_LOG = Prop("ATDebugLog.Enable", false)

    val SOLO_MINING_PASSPHRASES = Prop("SoloMiningPassphrases", emptyList<String>())
    val ALLOW_OTHER_SOLO_MINERS = Prop("AllowOtherSoloMiners", true)

    val NUM_PRE_VERIFIER_THREADS = Prop("PreVerifier.NumberOfInstances", 0)
    val MAX_CACHED_ENTITIES = Prop("MaxCachedEntities", 65536)
}
