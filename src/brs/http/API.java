package brs.http;

import brs.*;
import brs.assetexchange.AssetExchange;
import brs.deeplink.DeeplinkQRCodeGenerator;
import brs.feesuggestions.FeeSuggestionCalculator;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.*;
import brs.util.Subnet;
import brs.util.ThreadPool;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.RewriteRegexRule;
import org.eclipse.jetty.rewrite.handler.Rule;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.DoSFilter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

public final class API {

  private static final Logger logger = LoggerFactory.getLogger(API.class);

  @SuppressWarnings("squid:S1075")
  private static final String API_PATH = "/burst";
  @SuppressWarnings("squid:S1075")
  private static final String API_TEST_PATH = "/test";

  private final Server apiServer;

  public API(TransactionProcessor transactionProcessor,
      Blockchain blockchain, BlockchainProcessor blockchainProcessor, ParameterService parameterService,
      AccountService accountService, AliasService aliasService,
      AssetExchange assetExchange, EscrowService escrowService, DGSGoodsStoreService digitalGoodsStoreService,
      SubscriptionService subscriptionService, ATService atService,
      TimeService timeService, EconomicClustering economicClustering, PropertyService propertyService,
      ThreadPool threadPool, TransactionService transactionService, BlockService blockService,
      Generator generator, APITransactionManager apiTransactionManager, FeeSuggestionCalculator feeSuggestionCalculator, DeeplinkQRCodeGenerator deepLinkQRCodeGenerator, IndirectIncomingService indirectIncomingService) {

    List<String> allowedBotHostsList = propertyService.getStringList(Props.API_ALLOWED);
    Set<Subnet> allowedBotHosts;
    if (!allowedBotHostsList.contains("*")) {
      // Temp hashset to store allowed subnets
      Set<Subnet> allowedSubnets = new HashSet<>();

      for (String allowedHost : allowedBotHostsList) {
        try {
          allowedSubnets.add(Subnet.createInstance(allowedHost));
        }
        catch (UnknownHostException e) {
          logger.error("Error adding allowed host/subnet '" + allowedHost + "'", e);
        }
      }
      allowedBotHosts = Collections.unmodifiableSet(allowedSubnets);
    }
    else {
      allowedBotHosts = null;
    }

    boolean enableAPIServer = propertyService.getBoolean(Props.API_SERVER);
    if (enableAPIServer) {
      final String host = propertyService.getString(Props.API_LISTEN);
      final int    port = propertyService.getBoolean(Props.DEV_TESTNET) ? propertyService.getInt(Props.DEV_API_PORT) : propertyService.getInt(Props.API_PORT);
      apiServer = new Server();
      ServerConnector connector;

      boolean enableSSL = propertyService.getBoolean(Props.API_SSL);
      if (enableSSL) {
        logger.info("Using SSL (https) for the API server");
        HttpConfiguration httpsConfig = new HttpConfiguration();
        httpsConfig.setSecureScheme("https");
        httpsConfig.setSecurePort(port);
        httpsConfig.addCustomizer(new SecureRequestCustomizer());
        SslContextFactory sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath(propertyService.getString(Props.API_SSL_KEY_STORE_PATH));
        sslContextFactory.setKeyStorePassword(propertyService.getString(Props.API_SSL_KEY_STORE_PASSWORD));
        sslContextFactory.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA",
                                                 "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                                                 "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                                                 "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                                                 "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                                                 "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                                                 "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");
        sslContextFactory.setExcludeProtocols("SSLv3");
        connector = new ServerConnector(apiServer, new SslConnectionFactory(sslContextFactory, "http/1.1"),
                                        new HttpConnectionFactory(httpsConfig));
      }
      else {
        connector = new ServerConnector(apiServer);
      }

      connector.setHost(host);
      connector.setPort(port);
      connector.setIdleTimeout(propertyService.getInt(Props.API_SERVER_IDLE_TIMEOUT));
      // defaultProtocol
      // stopTimeout
      // acceptQueueSize
      connector.setReuseAddress(true);
      // soLingerTime
      apiServer.addConnector(connector);

      HandlerList apiHandlers = new HandlerList();

      ServletContextHandler apiHandler = new ServletContextHandler();
      String apiResourceBase = propertyService.getString(Props.API_UI_DIR);
      if (apiResourceBase != null) {
        ServletHolder defaultServletHolder = new ServletHolder(new DefaultServlet());
        defaultServletHolder.setInitParameter("resourceBase",    apiResourceBase);
        defaultServletHolder.setInitParameter("dirAllowed",      "false");
        defaultServletHolder.setInitParameter("welcomeServlets", "true");
        defaultServletHolder.setInitParameter("redirectWelcome", "true");
        defaultServletHolder.setInitParameter("gzip", "true");
        apiHandler.addServlet(defaultServletHolder, "/*");
        apiHandler.setWelcomeFiles(new String[]{"index.html"});
      }

      APIServlet apiServlet = new APIServlet(transactionProcessor, blockchain, blockchainProcessor, parameterService,
              accountService, aliasService, assetExchange, escrowService, digitalGoodsStoreService,
              subscriptionService, atService, timeService, economicClustering, transactionService, blockService, generator, propertyService,
              apiTransactionManager, feeSuggestionCalculator, deepLinkQRCodeGenerator, indirectIncomingService, allowedBotHosts);
      ServletHolder apiServletHolder = new ServletHolder(apiServlet);
      apiHandler.addServlet(apiServletHolder, API_PATH);
      
      if (propertyService.getBoolean(Props.JETTY_API_DOS_FILTER)) {
        FilterHolder dosFilterHolder = apiHandler.addFilter(DoSFilter.class, API_PATH, null);
        dosFilterHolder.setInitParameter("maxRequestsPerSec", propertyService.getString(Props.JETTY_API_DOS_FILTER_MAX_REQUEST_PER_SEC));
        dosFilterHolder.setInitParameter("throttledRequests", propertyService.getString(Props.JETTY_API_DOS_FILTER_THROTTLED_REQUESTS));
        dosFilterHolder.setInitParameter("delayMs",           propertyService.getString(Props.JETTY_API_DOS_FILTER_DELAY_MS));
        dosFilterHolder.setInitParameter("maxWaitMs",         propertyService.getString(Props.JETTY_API_DOS_FILTER_MAX_WAIT_MS));
        dosFilterHolder.setInitParameter("maxRequestMs",      propertyService.getString(Props.JETTY_API_DOS_FILTER_MAX_REQUEST_MS));
        dosFilterHolder.setInitParameter("maxthrottleMs",     propertyService.getString(Props.JETTY_API_DOS_FILTER_THROTTLE_MS));
        dosFilterHolder.setInitParameter("maxIdleTrackerMs",  propertyService.getString(Props.JETTY_API_DOS_FILTER_MAX_IDLE_TRACKER_MS));
        dosFilterHolder.setInitParameter("trackSessions",     propertyService.getString(Props.JETTY_API_DOS_FILTER_TRACK_SESSIONS));
        dosFilterHolder.setInitParameter("insertHeaders",     propertyService.getString(Props.JETTY_API_DOS_FILTER_INSERT_HEADERS));
        dosFilterHolder.setInitParameter("remotePort",        propertyService.getString(Props.JETTY_API_DOS_FILTER_REMOTE_PORT));
        dosFilterHolder.setInitParameter("ipWhitelist",       propertyService.getString(Props.JETTY_API_DOS_FILTER_IP_WHITELIST));
        dosFilterHolder.setInitParameter("managedAttr",       propertyService.getString(Props.JETTY_API_DOS_FILTER_MANAGED_ATTR));
        dosFilterHolder.setAsyncSupported(true);
      }

      apiHandler.addServlet(new ServletHolder(new APITestServlet(apiServlet, allowedBotHosts)), API_TEST_PATH);

      RewriteHandler rewriteHandler = new RewriteHandler();
      rewriteHandler.setRewriteRequestURI(true);
      rewriteHandler.setRewritePathInfo(false);
      rewriteHandler.setOriginalPathAttribute("requestedPath");
      rewriteHandler.setHandler(apiHandler);
      Rule rewriteToRoot = new RegexOrExistsRewriteRule(new File(propertyService.getString(Props.API_UI_DIR)), "^(?!"+regexpEscapeUrl(API_PATH)+"|"+regexpEscapeUrl(API_TEST_PATH)+").*$", "/index.html");
      rewriteHandler.addRule(rewriteToRoot);
      apiHandlers.addHandler(rewriteHandler);

      if (propertyService.getBoolean(Props.JETTY_API_GZIP_FILTER)) {
        GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setIncludedPaths(API_PATH);
        gzipHandler.setIncludedMethodList(propertyService.getString(Props.JETTY_API_GZIP_FILTER_METHODS));
        gzipHandler.setInflateBufferSize(propertyService.getInt(Props.JETTY_API_GZIP_FILTER_BUFFER_SIZE));
        gzipHandler.setMinGzipSize(propertyService.getInt(Props.JETTY_API_GZIP_FILTER_MIN_GZIP_SIZE));
        gzipHandler.setHandler(apiHandler);
        apiHandlers.addHandler(gzipHandler);
      } else {
        apiHandlers.addHandler(apiHandler);
      }

      apiServer.setHandler(apiHandlers);
      apiServer.setStopAtShutdown(true);

      threadPool.runBeforeStart(() -> {
        try {
          apiServer.start();
          logger.info("Started API server at {}:{}", host,    port);
        } catch (Exception e) {
          logger.error("Failed to start API server", e);
          throw new RuntimeException(e.toString(), e);
        }

      }, true);

    } else {
      apiServer = null;
      logger.info("API server not enabled");
    }

  }

  private String regexpEscapeUrl(String url) {
    return url.replace("/", "\\/");
  }

  public void shutdown() {
    if (apiServer != null) {
      try {
        apiServer.stop();
      } catch (Exception e) {
        logger.info("Failed to stop API server", e);
      }
    }
  }

  private static class RegexOrExistsRewriteRule extends RewriteRegexRule {

    private final File baseDirectory;

    RegexOrExistsRewriteRule(File baseDirectory, String regex, String replacement) {
      super(regex, replacement);
      this.baseDirectory = baseDirectory;
    }

    @Override
    public String apply(String target, HttpServletRequest request, HttpServletResponse response, Matcher matcher) throws IOException {
      return new File(baseDirectory, target).exists() ? target : super.apply(target, request, response, matcher);
    }
  }
}
