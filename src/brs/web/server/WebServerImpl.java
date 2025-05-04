package brs.web.server;

import brs.props.PropertyService;
import brs.props.Props;
import brs.util.Subnet;
import brs.web.api.http.ApiServlet;
import brs.web.api.http.LegacyDocsServlet;
import brs.web.api.ws.BlockchainEventNotifier;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.DoSFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.*;

public final class WebServerImpl implements WebServer {

  private static final Logger logger = LoggerFactory.getLogger(WebServerImpl.class);

  private static final String LEGACY_API_PATH = "/burst";
  private static final String API_PATH = "/api";

  private final org.eclipse.jetty.server.Server jettyServer;
  private BlockchainEventNotifier eventNotifier = null;

  private final WebServerContext context;

  public WebServerImpl(WebServerContext context) {

    this.context = context;
    boolean enableAPIServer = context.getPropertyService().getBoolean(Props.API_SERVER);
    if (enableAPIServer) {
      jettyServer = createServerInstance();
    } else {
      jettyServer = null;
      logger.info("Web server not enabled");
    }
  }

  private Server createServerInstance() {
    final Server jettyServer;
    jettyServer = new Server();
    ServletContextHandler servletContextHandler = new ServletContextHandler();
    ServerConnectorFactory connectorFactory = new ServerConnectorFactory(context, jettyServer);
    jettyServer.addConnector(connectorFactory.createHttpConnector());
    if(context.getPropertyService().getBoolean(Props.API_WEBSOCKET_ENABLE)) {
      eventNotifier = BlockchainEventNotifier.getInstance(context);
      jettyServer.addConnector(connectorFactory.createWebsocketConnector(servletContextHandler));
    }

    configureWebUI(servletContextHandler);
    configureHttpApi(servletContextHandler);

    HandlerList rootHandler = new HandlerList();
    rootHandler.addHandler(servletContextHandler);
    if (context.getPropertyService().getBoolean(Props.JETTY_API_GZIP_FILTER)) {
      GzipHandler gzipHandler = new GzipHandler();
      gzipHandler.setIncludedMethodList("GET,POST");
      gzipHandler.setMinGzipSize(context.getPropertyService().getInt(Props.JETTY_API_GZIP_FILTER_MIN_GZIP_SIZE));
      rootHandler.addHandler(gzipHandler);
    }
    jettyServer.setHandler(rootHandler);
    jettyServer.setStopAtShutdown(true);
    return jettyServer;
  }

  private void configureHttpApi(ServletContextHandler servletContextHandler) {

    Set<Subnet> allowedBotHosts = getAllowedBotHosts();
    ApiServlet apiServlet = new ApiServlet(context, allowedBotHosts);

    // set up HTTP API paths
    ServletHolder apiServletHolder = new ServletHolder(apiServlet);
    servletContextHandler.addServlet(apiServletHolder, API_PATH);
    servletContextHandler.addServlet(apiServletHolder, LEGACY_API_PATH);
    if (context.getPropertyService().getBoolean(Props.JETTY_API_DOS_FILTER)) {
      addDOSFilterToPath(API_PATH, servletContextHandler);
      addDOSFilterToPath(LEGACY_API_PATH, servletContextHandler);
    }

    // set up API docs
    String apiDocResourceBase = context.getPropertyService().getString(Props.API_DOC_MODE);
    if (apiDocResourceBase.equals("legacy")) {
      servletContextHandler.addServlet(new ServletHolder(
          new LegacyDocsServlet(apiServlet, allowedBotHosts, context.getPropertyService().getString(Props.NETWORK_NAME))),
        LegacyDocsServlet.API_DOC_PATH);
      logger.info("Legacy API docs enabled - you should consider setting API_DOC_MODE to 'modern' for better experience");
    } else if (apiDocResourceBase.equals("off")) {
      // no operation
      logger.info("API docs disabled");
    } else {
      logger.info("API docs enabled");
      ServletHolder defaultServletHolder = new ServletHolder(new DefaultServlet());
      defaultServletHolder.setInitParameter("resourceBase", "html");
      defaultServletHolder.setInitParameter("dirAllowed", "false");
      defaultServletHolder.setInitParameter("welcomeServlets", "true");
      defaultServletHolder.setInitParameter("redirectWelcome", "true");
      defaultServletHolder.setInitParameter("gzip", "true");
      servletContextHandler.addServlet(defaultServletHolder, "/api-doc/*");
      servletContextHandler.setWelcomeFiles(new String[]{"index.html"});
    }
  }

  private void configureWebUI(ServletContextHandler servletContextHandler) {
    String apiResourceBase = Paths.get(
      context.getPropertyService().getString(Props.API_UI_DIR)
    ).toAbsolutePath().toString();
    if (!apiResourceBase.isEmpty()) {
      ServletHolder defaultServletHolder = new ServletHolder(new DefaultServlet());
      defaultServletHolder.setInitParameter("resourceBase", apiResourceBase);
      defaultServletHolder.setInitParameter("dirAllowed", "false");
      defaultServletHolder.setInitParameter("welcomeServlets", "true");
      defaultServletHolder.setInitParameter("redirectWelcome", "true");
      defaultServletHolder.setInitParameter("gzip", "true");
      servletContextHandler.addServlet(defaultServletHolder, "/*");
      servletContextHandler.setWelcomeFiles(new String[]{"index.html"});
    }
  }


  @Override
  public void start() throws Exception {
    if (jettyServer == null) {
      return;
    }
    context.getThreadPool().runBeforeStart(() -> {
      try {
        jettyServer.start();
        logger.info("Web Server started successfully");
      } catch (Exception e) {
        logger.error("Failed to start API server", e);
        throw new RuntimeException(e.toString(), e);
      }
    }, true);
  }

  @Override
  public void shutdown() {
    if (jettyServer == null) {
      return;
    }

    try {
      if(eventNotifier != null) {
        eventNotifier.shutdown();
      }
      jettyServer.stop();
    } catch (Exception e) {
      logger.info("Failed to stop API server", e);
    }
  }


  private void addDOSFilterToPath(String path, ServletContextHandler servletContextHandler) {
    PropertyService propertyService = context.getPropertyService();
    FilterHolder dosFilterHolder = servletContextHandler.addFilter(DoSFilter.class, path, null);
    dosFilterHolder.setInitParameter("maxRequestsPerSec", propertyService.getString(Props.JETTY_API_DOS_FILTER_MAX_REQUEST_PER_SEC));
    dosFilterHolder.setInitParameter("throttledRequests", propertyService.getString(Props.JETTY_API_DOS_FILTER_THROTTLED_REQUESTS));
    dosFilterHolder.setInitParameter("delayMs", propertyService.getString(Props.JETTY_API_DOS_FILTER_DELAY_MS));
    dosFilterHolder.setInitParameter("maxWaitMs", propertyService.getString(Props.JETTY_API_DOS_FILTER_MAX_WAIT_MS));
    dosFilterHolder.setInitParameter("maxRequestMs", propertyService.getString(Props.JETTY_API_DOS_FILTER_MAX_REQUEST_MS));
    dosFilterHolder.setInitParameter("maxthrottleMs", propertyService.getString(Props.JETTY_API_DOS_FILTER_THROTTLE_MS));
    dosFilterHolder.setInitParameter("maxIdleTrackerMs", propertyService.getString(Props.JETTY_API_DOS_FILTER_MAX_IDLE_TRACKER_MS));
    dosFilterHolder.setInitParameter("trackSessions", propertyService.getString(Props.JETTY_API_DOS_FILTER_TRACK_SESSIONS));
    dosFilterHolder.setInitParameter("insertHeaders", propertyService.getString(Props.JETTY_API_DOS_FILTER_INSERT_HEADERS));
    dosFilterHolder.setInitParameter("remotePort", propertyService.getString(Props.JETTY_API_DOS_FILTER_REMOTE_PORT));
    dosFilterHolder.setInitParameter("ipWhitelist", propertyService.getString(Props.JETTY_API_DOS_FILTER_IP_WHITELIST));
    dosFilterHolder.setInitParameter("managedAttr", propertyService.getString(Props.JETTY_API_DOS_FILTER_MANAGED_ATTR));
    dosFilterHolder.setAsyncSupported(true);
  }

  private Set<Subnet> getAllowedBotHosts() {
    List<String> allowedBotHostsList = context.getPropertyService().getStringList(Props.API_ALLOWED);
    if (allowedBotHostsList.contains("*")) {
      return null;
    }
    // Temp hashset to store allowed subnets
    Set<Subnet> allowedSubnets = new HashSet<>();
    for (String allowedHost : allowedBotHostsList) {
      try {
        allowedSubnets.add(Subnet.createInstance(allowedHost));
      } catch (UnknownHostException e) {
        logger.error("Error adding allowed host/subnet '" + allowedHost + "'", e);
      }
    }
    return Collections.unmodifiableSet(allowedSubnets);
  }


}
