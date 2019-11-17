package brs

import brs.api.grpc.service.BrsService
import brs.api.http.API
import brs.api.http.APITransactionManagerImpl
import brs.at.*
import brs.db.sql.*
import brs.entity.DependencyProvider
import brs.objects.Constants
import brs.objects.Props
import brs.services.BlockchainProcessorService
import brs.services.PropertyService
import brs.services.impl.*
import brs.services.impl.GeneratorServiceImpl
import brs.transaction.type.TransactionType
import brs.util.LoggerConfigurator
import brs.util.Time
import brs.util.Version
import brs.util.logging.safeError
import brs.util.logging.safeInfo
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import kotlin.math.max
import kotlin.system.exitProcess

class Burst(properties: Properties, addShutdownHook: Boolean = true) {
    val dp = DependencyProvider()

    init {
        dp.propertyService = PropertyServiceImpl(properties)
        validateVersionNotDev(dp.propertyService)

        if (addShutdownHook) Runtime.getRuntime().addShutdownHook(Thread(Runnable { shutdown() }))

        try {
            val startTime = System.currentTimeMillis()
            logger.safeInfo {
"""
**********
SYSTEM INFORMATION
RT: ${System.getProperty("java.runtime.name")}, Version: ${System.getProperty("java.runtime.version")}
VM: ${System.getProperty("java.vm.name")}, Version: ${System.getProperty("java.vm.version")}
OS: ${System.getProperty("os.name")}, Version: ${System.getProperty("os.version")}, Architecture: ${System.getProperty("os.arch")}
**********"""
            }
            Constants.init(dp)
            dp.taskSchedulerService = RxJavaTaskSchedulerService()
            dp.atApi = AtApiPlatformImpl(dp)
            dp.atApiController = AtApiController(dp)
            dp.atController = AtController(dp)
            if (dp.propertyService.get(Props.GPU_ACCELERATION)) {
                dp.oclPocService = OclPocServiceImpl(dp)
            }
            dp.timeService = TimeServiceImpl()
            dp.derivedTableService = DerivedTableServiceImpl()
            dp.statisticsService = StatisticsServiceImpl(dp)
            dp.dbCacheService = DBCacheServiceImpl(dp)
            LoggerConfigurator.init()
            dp.db = SqlDb(dp)
            dp.blockDb = SqlBlockDb(dp)
            dp.transactionDb = SqlTransactionDb(dp)
            dp.peerDb = SqlPeerDb(dp)
            dp.accountStore = SqlAccountStore(dp)
            dp.aliasStore = SqlAliasStore(dp)
            dp.assetStore = SqlAssetStore(dp)
            dp.assetTransferStore = SqlAssetTransferStore(dp)
            dp.atStore = SqlATStore(dp)
            dp.digitalGoodsStoreStore = SqlDigitalGoodsStoreStore(dp)
            dp.escrowStore = SqlEscrowStore(dp)
            dp.orderStore = SqlOrderStore(dp)
            dp.tradeStore = SqlTradeStore(dp)
            dp.subscriptionStore = SqlSubscriptionStore(dp)
            dp.unconfirmedTransactionService = UnconfirmedTransactionServiceImpl(dp)
            dp.indirectIncomingStore = SqlIndirectIncomingStore(dp)
            dp.blockchainStore = SqlBlockchainStore(dp)
            dp.blockchainService = BlockchainServiceImpl(dp)
            dp.aliasService = AliasServiceImpl(dp)
            dp.fluxCapacitorService = FluxCapacitorServiceImpl(dp)
            dp.transactionTypes = TransactionType.getTransactionTypes(dp)
            dp.blockService = BlockServiceImpl(dp)
            dp.blockchainProcessorService = BlockchainProcessorServiceImpl(dp)
            dp.atConstants = AtConstants(dp)
            dp.economicClusteringService = EconomicClusteringServiceImpl(dp)
            dp.generatorService =
                if (dp.propertyService.get(Props.DEV_TESTNET) && dp.propertyService.get(Props.DEV_MOCK_MINING))
                    GeneratorServiceImpl.MockGeneratorService(dp)
                else
                    GeneratorServiceImpl(dp)
            dp.accountService = AccountServiceImpl(dp)
            dp.transactionService = TransactionServiceImpl(dp)
            dp.transactionProcessorService = TransactionProcessorServiceImpl(dp)
            dp.atService = ATServiceImpl(dp)
            dp.subscriptionService = SubscriptionServiceImpl(dp)
            dp.digitalGoodsStoreService = DigitalGoodsStoreServiceImpl(dp)
            dp.escrowService = EscrowServiceImpl(dp)
            dp.assetExchangeService = AssetExchangeServiceImpl(dp)
            dp.downloadCacheService = DownloadCacheServiceImpl(dp)
            dp.indirectIncomingService = IndirectIncomingServiceImpl(dp)
            dp.feeSuggestionService = FeeSuggestionServiceImpl(dp)
            dp.deeplinkQRCodeGeneratorService = DeeplinkQRCodeGeneratorServiceImpl()
            dp.deeplinkGeneratorService = DeeplinkGeneratorServiceImpl()
            dp.parameterService = ParameterServiceImpl(dp)
            dp.blockchainProcessorService.addListener(
                BlockchainProcessorService.Event.AFTER_BLOCK_APPLY,
                AT.handleATBlockTransactionsListener(dp)
            )
            dp.blockchainProcessorService.addListener(
                BlockchainProcessorService.Event.AFTER_BLOCK_APPLY,
                DigitalGoodsStoreServiceImpl.expiredPurchaseListener(dp)
            )
            dp.apiTransactionManager = APITransactionManagerImpl(dp)
            dp.peerService = PeerServiceImpl(dp)
            dp.api = API(dp)

            if (dp.propertyService.get(Props.API_V2_SERVER)) {
                val hostname = dp.propertyService.get(Props.API_V2_LISTEN)
                val port =
                    if (dp.propertyService.get(Props.DEV_TESTNET)) dp.propertyService.get(Props.DEV_API_V2_PORT) else dp.propertyService.get(
                        Props.API_V2_PORT
                    )
                logger.safeInfo { "Starting V2 API Server on port $port" }
                dp.apiV2Server = BrsService(dp).start(hostname, port)
            } else {
                logger.safeInfo { "Not starting V2 API Server - it is disabled." }
            }

            val timeMultiplier =
                if (dp.propertyService.get(Props.DEV_TESTNET) && dp.propertyService.get(Props.DEV_OFFLINE)) dp.propertyService.get(
                    Props.DEV_TIMEWARP
                ).coerceAtLeast(1) else 1

            logger.safeInfo { "Starting Task Scheduler" }
            dp.taskSchedulerService.start()
            if (timeMultiplier > 1) {
                dp.timeService.setTime(
                    Time.FasterTime(
                        max(
                            dp.timeService.epochTime,
                            dp.blockchainService.lastBlock.timestamp
                        ), timeMultiplier
                    )
                )
                logger.safeInfo { "TIME WILL FLOW $timeMultiplier TIMES FASTER!" }
            }

            val currentTime = System.currentTimeMillis()
            logger.safeInfo { "Initialization took ${currentTime - startTime} ms" }
            logger.safeInfo { "$APPLICATION $VERSION started successfully!" }

            if (dp.propertyService.get(Props.DEV_TESTNET)) {
                logger.safeInfo { "RUNNING ON TESTNET - DO NOT USE REAL ACCOUNTS!" }
            }
        } catch (e: Exception) {
            logger.safeError(e) { e.message }
            exitProcess(1)
        }
    }

    private fun validateVersionNotDev(propertyService: PropertyService) {
        if (VERSION.isPrelease && !propertyService.get(Props.DEV_TESTNET)) {
            logger.safeError { "THIS IS A DEVELOPMENT WALLET, PLEASE DO NOT USE THIS" }
            exitProcess(0)
        }
    }

    private fun shutdown() {
        shutdown(false)
    }

    fun shutdown(ignoreDBShutdown: Boolean) {
        logger.safeInfo { "Shutting down..." }
        try {
            dp.api.shutdown()
        } catch (ignored: UninitializedPropertyAccessException) {
            // Ignore
        }
        try {
            dp.apiV2Server.shutdownNow()
        } catch (ignored: UninitializedPropertyAccessException) {
            // Ignore
        }
        try {
            dp.peerService.shutdown()
        } catch (ignored: UninitializedPropertyAccessException) {
            // Ignore
        }
        try {
            dp.taskSchedulerService.shutdown()
        } catch (ignored: UninitializedPropertyAccessException) {
            // Ignore
        }
        if (!ignoreDBShutdown) {
            try {
                dp.db.shutdown()
            } catch (ignored: UninitializedPropertyAccessException) {
                // Ignore
            }
        }
        try {
            dp.dbCacheService.close()
        } catch (ignored: UninitializedPropertyAccessException) {
            // Ignore
        }
        try {
            if (dp.blockchainProcessorService.oclVerify) {
                dp.oclPocService.destroy()
            }
        } catch (ignored: UninitializedPropertyAccessException) {
            // Ignore
        }
        logger.safeInfo { "$APPLICATION $VERSION stopped." }
        LoggerConfigurator.shutdown()
    }

    companion object {
        val VERSION = Version.parse("v3.0.0-dev")
        const val APPLICATION = "BRS"
        private const val DEFAULT_PROPERTIES_NAME = "brs-default.properties"
        private val logger = LoggerFactory.getLogger(Burst::class.java)

        private fun loadProperties(): Properties {
            val defaultProperties = Properties()

            // TODO this can be refactored to be cleaner.
            logger.safeInfo { "Initializing Burst Reference Software ($APPLICATION) version $VERSION" }
            try {
                ClassLoader.getSystemResourceAsStream(DEFAULT_PROPERTIES_NAME).use { input ->
                    if (input != null) {
                        defaultProperties.load(input)
                    } else {
                        val configFile = System.getProperty(DEFAULT_PROPERTIES_NAME)

                        if (configFile != null) {
                            try {
                                FileInputStream(configFile).use { fis -> defaultProperties.load(fis) }
                            } catch (e: IOException) {
                                throw RuntimeException("Error loading $DEFAULT_PROPERTIES_NAME from $configFile")
                            }

                        } else {
                            throw RuntimeException("$DEFAULT_PROPERTIES_NAME not in classpath and system property $DEFAULT_PROPERTIES_NAME not defined either")
                        }
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException("Error loading $DEFAULT_PROPERTIES_NAME", e)
            }

            val properties = Properties(defaultProperties)
            try {
                ClassLoader.getSystemResourceAsStream("brs.properties").use { input ->
                    if (input != null) { // parse if brs.properties was loaded
                        properties.load(input)
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException("Error loading brs.properties", e)
            }

            return properties
        }

        @JvmStatic
        fun main(args: Array<String>) {
            init()
        }

        fun init(addShutdownHook: Boolean = true): Burst {
            return Burst(loadProperties(), addShutdownHook)
        }
    }
}
