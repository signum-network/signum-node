package brs;

import brs.assetexchange.AssetExchange;
import brs.assetexchange.AssetExchangeImpl;
import brs.at.AT;
import brs.db.BlockDb;
import brs.db.TransactionDb;
import brs.db.cache.DBCacheManagerImpl;
import brs.db.sql.Db;
import brs.db.store.BlockchainStore;
import brs.db.store.Dbs;
import brs.db.store.DerivedTableManager;
import brs.db.store.Stores;
import brs.deeplink.DeeplinkQRCodeGenerator;
import brs.feesuggestions.FeeSuggestionCalculator;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxCapacitorImpl;
import brs.grpc.proto.BrsService;
import brs.http.API;
import brs.http.APITransactionManager;
import brs.http.APITransactionManagerImpl;
import brs.peer.Peers;
import brs.props.PropertyService;
import brs.props.PropertyServiceImpl;
import brs.props.Props;
import brs.services.*;
import brs.services.impl.*;
import brs.statistics.StatisticsManagerImpl;
import brs.util.DownloadCacheImpl;
import brs.util.LoggerConfigurator;
import brs.util.ThreadPool;
import brs.util.Time;
import io.grpc.Server;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Burst {

  public static final Version VERSION = Version.parse("v3.0.0-alpha12");

  public static final String APPLICATION = "BRS";

  public static final String CONF_FOLDER = "./conf";
  public static final String DEFAULT_PROPERTIES_NAME = "brs-default.properties";
  public static final String PROPERTIES_NAME = "brs.properties";

  public static final Option CONF_FOLDER_OPTION = Option.builder("c")
		  .longOpt("config")
		  .argName("conf folder")
		  .numberOfArgs(1)
		  .desc("The configuration folder to use")
		  .build();

  public static final Options CLI_OPTIONS = new Options()
		  .addOption(CONF_FOLDER_OPTION)
		  .addOption(Option.builder("l")
	        		.longOpt("headless")
	        		.desc("Run in headless mode")
	        		.build())
		  .addOption(Option.builder("h")
	        		.longOpt("help")
	        		.build());

  private static final Logger logger = LoggerFactory.getLogger(Burst.class);

  private static Stores stores;
  private static Dbs dbs;

  private static ThreadPool threadPool;

  private static BlockchainImpl blockchain;
  private static BlockchainProcessorImpl blockchainProcessor;
  private static TransactionProcessorImpl transactionProcessor;

  private static PropertyService propertyService;
  private static FluxCapacitor fluxCapacitor;

  private static DBCacheManagerImpl dbCacheManager;

  private static API api;
  private static Server apiV2Server;

  private static PropertyService loadProperties(String confFolder) {
    logger.info("Initializing Burst Reference Software (BRS) version {}", VERSION);
    
    logger.info("Configurations from folder {}", confFolder);
    Properties defaultProperties = new Properties();
    try (InputStream is = new FileInputStream(new File(confFolder, DEFAULT_PROPERTIES_NAME))) {
       defaultProperties.load(is);
    } catch (IOException e) {
      throw new RuntimeException("Error loading " + DEFAULT_PROPERTIES_NAME, e);
    }

    Properties properties = new Properties(defaultProperties);
    try (InputStream is = new FileInputStream(new File(confFolder, PROPERTIES_NAME))) {
      if (is != null) { // parse if brs.properties was loaded
        properties.load(is);
      }
    } catch (IOException e) {
      logger.info("Custom user properties file {} not loaded", PROPERTIES_NAME);
    }

    return new PropertyServiceImpl(properties);
  }

  private Burst() {
  } // never

  public static Blockchain getBlockchain() {
    return blockchain;
  }

  public static BlockchainProcessorImpl getBlockchainProcessor() {
    return blockchainProcessor;
  }

  public static TransactionProcessorImpl getTransactionProcessor() {
    return transactionProcessor;
  }

  public static Stores getStores() {
    return stores;
  }

  public static Dbs getDbs() {
    return dbs;
  }

  public static void main(String []args) {
    Runtime.getRuntime().addShutdownHook(new Thread(Burst::shutdown));
    String confFolder = CONF_FOLDER;
    try {
      CommandLine cmd = new DefaultParser().parse(CLI_OPTIONS, args);
      if(cmd.hasOption(CONF_FOLDER_OPTION.getOpt()))
    	  confFolder = cmd.getOptionValue(CONF_FOLDER_OPTION.getOpt());
    }
    catch (Exception e) {
    	logger.error("Exception parsing command line arguments", e);
	}
    init(confFolder);
  }

  private static boolean validateVersionNotDev(PropertyService propertyService) {
    if(VERSION.isPrelease() && !propertyService.getBoolean(Props.DEV_TESTNET)) {
      logger.error("THIS IS A DEVELOPMENT VERSION, PLEASE DO NOT USE THIS ON MAINNET");
      return false;
    }
    return true;
  }
  
  public static void init(Properties customProperties) {
    loadWallet(new PropertyServiceImpl(customProperties));
  }

  private static void init(String confFolder) {
    loadWallet(loadProperties(confFolder));
  }

  private static void loadWallet(PropertyService propertyService) {
    LoggerConfigurator.init();

    Burst.propertyService = propertyService;
	if(!validateVersionNotDev(propertyService))
		return;

    try {
      long startTime = System.currentTimeMillis();

      final TimeService timeService = new TimeServiceImpl();

      final DerivedTableManager derivedTableManager = new DerivedTableManager();

      final StatisticsManagerImpl statisticsManager = new StatisticsManagerImpl(timeService);
      dbCacheManager = new DBCacheManagerImpl(statisticsManager);

      threadPool = new ThreadPool(propertyService);

      Db.init(propertyService, dbCacheManager);
      dbs = Db.getDbsByDatabaseType();

      stores = new Stores(derivedTableManager, dbCacheManager, timeService, propertyService, dbs.getTransactionDb());

      final TransactionDb transactionDb = dbs.getTransactionDb();
      final BlockDb blockDb =  dbs.getBlockDb();
      final BlockchainStore blockchainStore = stores.getBlockchainStore();
      blockchain = new BlockchainImpl(transactionDb, blockDb, blockchainStore);

      final AliasService aliasService = new AliasServiceImpl(stores.getAliasStore());
      fluxCapacitor = new FluxCapacitorImpl(blockchain, propertyService);

      EconomicClustering economicClustering = new EconomicClustering(blockchain);

      final AccountService accountService = new AccountServiceImpl(stores.getAccountStore(), stores.getAssetTransferStore());

      final DownloadCacheImpl downloadCache = new DownloadCacheImpl(propertyService, fluxCapacitor, blockchain);

      final Generator generator = propertyService.getBoolean(Props.DEV_MOCK_MINING) ? new GeneratorImpl.MockGenerator(propertyService, blockchain, timeService, fluxCapacitor) : new GeneratorImpl(blockchain, downloadCache, accountService, timeService, fluxCapacitor);

      final TransactionService transactionService = new TransactionServiceImpl(accountService, blockchain);

      transactionProcessor = new TransactionProcessorImpl(propertyService, economicClustering, blockchain, stores, timeService, dbs,
          accountService, transactionService, threadPool);

      final ATService atService = new ATServiceImpl(stores.getAtStore());
      final SubscriptionService subscriptionService = new SubscriptionServiceImpl(stores.getSubscriptionStore(), transactionDb, blockchain, aliasService, accountService);
      final DGSGoodsStoreService digitalGoodsStoreService = new DGSGoodsStoreServiceImpl(blockchain, stores.getDigitalGoodsStoreStore(), accountService);
      final EscrowService escrowService = new EscrowServiceImpl(stores.getEscrowStore(), blockchain, aliasService, accountService);

      final AssetExchange assetExchange = new AssetExchangeImpl(accountService, stores.getTradeStore(), stores.getAccountStore(), stores.getAssetTransferStore(), stores.getAssetStore(), stores.getOrderStore());

      final IndirectIncomingService indirectIncomingService = new IndirectIncomingServiceImpl(stores.getIndirectIncomingStore(), propertyService);

      TransactionType.init(blockchain, fluxCapacitor, accountService, digitalGoodsStoreService, aliasService, assetExchange, subscriptionService, escrowService);

      final BlockService blockService = new BlockServiceImpl(accountService, transactionService, blockchain, downloadCache, generator);
      blockchainProcessor = new BlockchainProcessorImpl(threadPool, blockService, transactionProcessor, blockchain, propertyService, subscriptionService,
          timeService, derivedTableManager,
          blockDb, transactionDb, economicClustering, blockchainStore, stores, escrowService, transactionService, downloadCache, generator, statisticsManager,
          dbCacheManager, accountService, indirectIncomingService);

      final FeeSuggestionCalculator feeSuggestionCalculator = new FeeSuggestionCalculator(blockchainProcessor, stores.getUnconfirmedTransactionStore());

      generator.generateForBlockchainProcessor(threadPool, blockchainProcessor);

      final DeeplinkQRCodeGenerator deepLinkQRCodeGenerator = new DeeplinkQRCodeGenerator();

      final ParameterService parameterService = new ParameterServiceImpl(accountService, aliasService, assetExchange,
          digitalGoodsStoreService, blockchain, blockchainProcessor, transactionProcessor, atService);

      addBlockchainListeners(blockchainProcessor, accountService, digitalGoodsStoreService, blockchain, dbs.getTransactionDb());

      final APITransactionManager apiTransactionManager = new APITransactionManagerImpl(parameterService, transactionProcessor, blockchain, accountService, transactionService);

      Peers.init(timeService, accountService, blockchain, transactionProcessor, blockchainProcessor, propertyService, threadPool);

      api = new API(transactionProcessor, blockchain, blockchainProcessor, parameterService,
          accountService, aliasService, assetExchange, escrowService, digitalGoodsStoreService,
          subscriptionService, atService, timeService, economicClustering, propertyService, threadPool,
          transactionService, blockService, generator, apiTransactionManager, feeSuggestionCalculator, deepLinkQRCodeGenerator, indirectIncomingService);

      if (propertyService.getBoolean(Props.API_V2_SERVER)) {
          int port = propertyService.getBoolean(Props.DEV_TESTNET) ? propertyService.getInt(Props.DEV_API_V2_PORT) : propertyService.getInt(Props.API_V2_PORT);
          logger.info("Starting V2 API Server on port {}", port);
          String hostname = propertyService.getString(Props.API_V2_LISTEN);
          apiV2Server = new BrsService(blockchainProcessor, blockchain, blockService, accountService, generator, transactionProcessor, timeService, feeSuggestionCalculator, atService, aliasService, indirectIncomingService, fluxCapacitor, escrowService, assetExchange, subscriptionService, digitalGoodsStoreService, propertyService).start(hostname, port);
      } else {
          logger.info("Not starting V2 API Server - it is disabled.");
      }

      if (propertyService.getBoolean(Props.BRS_DEBUG_TRACE_ENABLED))
        DebugTrace.init(propertyService, blockchainProcessor, accountService, assetExchange, digitalGoodsStoreService);

      int timeMultiplier = (propertyService.getBoolean(Props.DEV_TESTNET) && propertyService.getBoolean(Props.DEV_OFFLINE)) ? Math.max(propertyService.getInt(Props.DEV_TIMEWARP), 1) : 1;

      threadPool.start(timeMultiplier);
      if (timeMultiplier > 1) {
        timeService.setTime(new Time.FasterTime(Math.max(timeService.getEpochTime(), getBlockchain().getLastBlock().getTimestamp()), timeMultiplier));
        logger.info("TIME WILL FLOW {} TIMES FASTER!", timeMultiplier);
      }

      long currentTime = System.currentTimeMillis();
      logger.info("Initialization took {} ms", currentTime - startTime);
      logger.info("BRS {} started successfully.", VERSION);

      if (propertyService.getBoolean(Props.DEV_TESTNET)) {
        logger.info("RUNNING ON TESTNET!");
      }
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      System.exit(1);
    }
    (new Thread(Burst::commandHandler)).start();
  }

  private static void addBlockchainListeners(BlockchainProcessor blockchainProcessor, AccountService accountService, DGSGoodsStoreService goodsService, Blockchain blockchain,
      TransactionDb transactionDb) {

    final AT.HandleATBlockTransactionsListener handleATBlockTransactionListener = new AT.HandleATBlockTransactionsListener(accountService, blockchain, transactionDb);
    final DGSGoodsStoreServiceImpl.ExpiredPurchaseListener devNullListener = new DGSGoodsStoreServiceImpl.ExpiredPurchaseListener(accountService, goodsService);

    blockchainProcessor.addListener(handleATBlockTransactionListener, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);
    blockchainProcessor.addListener(devNullListener, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);
  }

  private static void shutdown() {
    shutdown(false);
  }

  private static void commandHandler() {
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    try {
      String command;
      while ( ( command = reader.readLine() ) != null){
        logger.debug("received command: >{}<", command);
        if ( command.equals(".shutdown") ) {
          shutdown(false);
          System.exit(0);
        } else if ( command.startsWith(".popoff ") ) {
          Pattern r = Pattern.compile("^\\.popoff (\\d+)$");
          Matcher m = r.matcher(command);
          if (m.find()) {
            int numBlocks = Integer.parseInt(m.group(1));
            if (numBlocks > 0) {
              blockchainProcessor.popOffTo(blockchain.getHeight() - numBlocks);
            }
          }
        }
      }
    } catch ( IOException e ) {
      // ignore
    }
  }

  public static void shutdown(boolean ignoreDBShutdown) {
    logger.info("Shutting down...");
    if (api != null)
      api.shutdown();
    if (apiV2Server != null)
      apiV2Server.shutdownNow();
    if (threadPool != null) {
      Peers.shutdown(threadPool);
      threadPool.shutdown();
    }
    if(! ignoreDBShutdown) {
      Db.shutdown();
    }
    if (dbCacheManager != null)
      dbCacheManager.close();
    if (blockchainProcessor != null && blockchainProcessor.getOclVerify()) {
      OCLPoC.destroy();
    }
    logger.info("BRS {} stopped.", VERSION);
    LoggerConfigurator.shutdown();
  }

  public static PropertyService getPropertyService() {
    return propertyService;
  }

  public static FluxCapacitor getFluxCapacitor() { return fluxCapacitor; }

}
