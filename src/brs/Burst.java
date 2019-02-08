package brs;

import brs.AT.HandleATBlockTransactionsListener;
import brs.assetexchange.AssetExchange;
import brs.assetexchange.AssetExchangeImpl;
import brs.blockchainlistener.DevNullListener;
import brs.db.BlockDb;
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
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static brs.schema.Tables.UNCONFIRMED_TRANSACTION;

public final class Burst {

  public static final Version VERSION = Version.parse("v2.3.0-dev");
  public static final String APPLICATION = "BRS";

  private static final String DEFAULT_PROPERTIES_NAME = "brs-default.properties";

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

  private static PropertyService loadProperties() {
    final Properties defaultProperties = new Properties();

    logger.info("Initializing Burst Reference Software (BRS) version {}", VERSION);
    try (InputStream is = ClassLoader.getSystemResourceAsStream(DEFAULT_PROPERTIES_NAME)) {
      if (is != null) {
        defaultProperties.load(is);
      } else {
        String configFile = System.getProperty(DEFAULT_PROPERTIES_NAME);

        if (configFile != null) {
          try (InputStream fis = new FileInputStream(configFile)) {
            defaultProperties.load(fis);
          } catch (IOException e) {
            throw new RuntimeException("Error loading " + DEFAULT_PROPERTIES_NAME + " from " + configFile);
          }
        } else {
          throw new RuntimeException(DEFAULT_PROPERTIES_NAME + " not in classpath and system property " + DEFAULT_PROPERTIES_NAME + " not defined either");
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Error loading " + DEFAULT_PROPERTIES_NAME, e);
    }

    Properties properties;
    try (InputStream is = ClassLoader.getSystemResourceAsStream("brs.properties")) {
      properties = new Properties(defaultProperties);
      if (is != null) { // parse if brs.properties was loaded
        properties.load(is);
      }
    } catch (IOException e) {
      throw new RuntimeException("Error loading brs.properties", e);
    }

    return new PropertyServiceImpl(properties);
  }

  private Burst() {
  } // never

  public static BlockchainImpl getBlockchain() {
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

  public static void main(String[] args) {
    Runtime.getRuntime().addShutdownHook(new Thread(Burst::shutdown));
    init();
  }

  private static void validateVersionNotDev(PropertyService propertyService) {
    if(VERSION.isPrelease() && !propertyService.getBoolean(Props.DEV_TESTNET)) {
      logger.error("THIS IS A DEVELOPMENT WALLET, PLEASE DO NOT USE THIS");
      System.exit(0);
    }
  }

  public static void init(Properties customProperties) {
    loadWallet(new PropertyServiceImpl(customProperties));
  }

  private static void init() {
    loadWallet(loadProperties());
  }

  private static void loadWallet(PropertyService propertyService) {
    validateVersionNotDev(propertyService);
    Burst.propertyService = propertyService;

    try {
      long startTime = System.currentTimeMillis();

      final TimeService timeService = new TimeServiceImpl();

      final DerivedTableManager derivedTableManager = new DerivedTableManager();

      final StatisticsManagerImpl statisticsManager = new StatisticsManagerImpl(timeService);
      dbCacheManager = new DBCacheManagerImpl(statisticsManager);

      threadPool = new ThreadPool(propertyService);

      LoggerConfigurator.init();

      Db.init(propertyService, dbCacheManager);
      dbs = Db.getDbsByDatabaseType();

      stores = new Stores(derivedTableManager, dbCacheManager, timeService, propertyService);

      final TransactionDb transactionDb = dbs.getTransactionDb();
      final BlockDb blockDb =  dbs.getBlockDb();
      final BlockchainStore blockchainStore = stores.getBlockchainStore();
      blockchain = new BlockchainImpl(transactionDb, blockDb, blockchainStore);

      final AliasService aliasService = new AliasServiceImpl(stores.getAliasStore());
      fluxCapacitor = new FluxCapacitorImpl(blockchain, propertyService);

      EconomicClustering economicClustering = new EconomicClustering(blockchain);

      final Generator generator = propertyService.getBoolean(Props.DEV_MOCK_MINING) ? new GeneratorImpl.MockGenerator(propertyService, blockchain, timeService, fluxCapacitor) : new GeneratorImpl(blockchain, timeService, fluxCapacitor);

      final AccountService accountService = new AccountServiceImpl(stores.getAccountStore(), stores.getAssetTransferStore());

      final TransactionService transactionService = new TransactionServiceImpl(accountService, blockchain);

      transactionProcessor = new TransactionProcessorImpl(propertyService, economicClustering, blockchain, stores, timeService, dbs,
          accountService, transactionService, threadPool);

      final ATService atService = new ATServiceImpl(stores.getAtStore());
      final SubscriptionService subscriptionService = new SubscriptionServiceImpl(stores.getSubscriptionStore(), transactionDb, blockchain, aliasService, accountService);
      final DGSGoodsStoreService digitalGoodsStoreService = new DGSGoodsStoreServiceImpl(blockchain, stores.getDigitalGoodsStoreStore(), accountService);
      final EscrowService escrowService = new EscrowServiceImpl(stores.getEscrowStore(), blockchain, aliasService, accountService);

      final AssetExchange assetExchange = new AssetExchangeImpl(accountService, stores.getTradeStore(), stores.getAccountStore(), stores.getAssetTransferStore(), stores.getAssetStore(), stores.getOrderStore());

      final DownloadCacheImpl downloadCache = new DownloadCacheImpl(propertyService, fluxCapacitor, blockchain);

      final BlockService blockService = new BlockServiceImpl(accountService, transactionService, blockchain, downloadCache, generator);
      blockchainProcessor = new BlockchainProcessorImpl(threadPool, blockService, transactionProcessor, blockchain, propertyService, subscriptionService,
          timeService, derivedTableManager,
          blockDb, transactionDb, economicClustering, blockchainStore, stores, escrowService, transactionService, downloadCache, generator, statisticsManager,
          dbCacheManager, accountService);

      final FeeSuggestionCalculator feeSuggestionCalculator = new FeeSuggestionCalculator(blockchainProcessor, blockchainStore, 10);

      generator.generateForBlockchainProcessor(threadPool, blockchainProcessor);

      final DeeplinkQRCodeGenerator deepLinkQRCodeGenerator = new DeeplinkQRCodeGenerator();

      final ParameterService parameterService = new ParameterServiceImpl(accountService, aliasService, assetExchange,
          digitalGoodsStoreService, blockchain, blockchainProcessor, transactionProcessor, atService);

      addBlockchainListeners(blockchainProcessor, accountService, digitalGoodsStoreService, blockchain, dbs.getTransactionDb());

      final APITransactionManager apiTransactionManager = new APITransactionManagerImpl(parameterService, transactionProcessor, blockchain, accountService, transactionService);

      Peers.init(timeService, accountService, blockchain, transactionProcessor, blockchainProcessor, propertyService, threadPool);

      TransactionType.init(blockchain, fluxCapacitor, accountService, digitalGoodsStoreService, aliasService, assetExchange, subscriptionService, escrowService);

      api = new API(transactionProcessor, blockchain, blockchainProcessor, parameterService,
          accountService, aliasService, assetExchange, escrowService, digitalGoodsStoreService,
          subscriptionService, atService, timeService, economicClustering, propertyService, threadPool,
          transactionService, blockService, generator, apiTransactionManager, feeSuggestionCalculator, deepLinkQRCodeGenerator);

      DebugTrace.init(propertyService, blockchainProcessor, accountService, assetExchange, digitalGoodsStoreService);

      // backward compatibility for those who have some unconfirmed transactions in their db
      try {
        stores.beginTransaction();
        try (DSLContext ctx = Db.getDSLContext()) {
          ResultSet rs = ctx.selectFrom(UNCONFIRMED_TRANSACTION).fetchResultSet();
          while ( rs.next() ) {
            byte[] transactionBytes = rs.getBytes("transaction_bytes");
            Transaction transaction = Transaction.parseTransaction(transactionBytes);
            transaction.setHeight(rs.getInt("transaction_height"));
            transactionService.undoUnconfirmed(transaction);
          }
          ctx.truncate(UNCONFIRMED_TRANSACTION).execute();
        }
        accountService.flushAccountTable();
        stores.commitTransaction();
      } catch (Exception e) {
        logger.error(e.toString(), e);
        stores.rollbackTransaction();
        throw e;
      } finally {
        stores.endTransaction();
      }

      int timeMultiplier = (propertyService.getBoolean(Props.DEV_TESTNET) && propertyService.getBoolean(Props.DEV_OFFLINE)) ? Math.max(propertyService.getInt(Props.DEV_TIMEWARP), 1) : 1;

      threadPool.start(timeMultiplier);
      if (timeMultiplier > 1) {
        timeService.setTime(new Time.FasterTime(Math.max(timeService.getEpochTime(), getBlockchain().getLastBlock().getTimestamp()), timeMultiplier));
        logger.info("TIME WILL FLOW " + timeMultiplier + " TIMES FASTER!");
      }

      long currentTime = System.currentTimeMillis();
      logger.info("Initialization took " + (currentTime - startTime) + " ms");
      logger.info("BRS " + VERSION + " started successfully.");

      if (propertyService.getBoolean(Props.DEV_TESTNET)) {
        logger.info("RUNNING ON TESTNET - DO NOT USE REAL ACCOUNTS!");
      }
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      System.exit(1);
    }
    (new Thread(Burst::commandHandler)).start();
  }

  private static void addBlockchainListeners(BlockchainProcessor blockchainProcessor, AccountService accountService, DGSGoodsStoreService goodsService, Blockchain blockchain,
      TransactionDb transactionDb) {

    final HandleATBlockTransactionsListener handleATBlockTransactionListener = new HandleATBlockTransactionsListener(accountService, blockchain, transactionDb);
    final DevNullListener devNullListener = new DevNullListener(accountService, goodsService);

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
        logger.debug("received command: >" + command + "<");
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
    Peers.shutdown(threadPool);
    threadPool.shutdown();
    if(! ignoreDBShutdown) {
      Db.shutdown();
    }
    dbCacheManager.close();
    if (blockchainProcessor != null && blockchainProcessor.getOclVerify()) {
      OCLPoC.destroy();
    }
    logger.info("BRS " + VERSION + " stopped.");
    LoggerConfigurator.shutdown();
  }

  public static PropertyService getPropertyService() {
    return propertyService;
  }

  public static FluxCapacitor getFluxCapacitor() { return fluxCapacitor; }

}
