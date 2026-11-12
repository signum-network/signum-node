package brs.web.api.ws;

import org.eclipse.jetty.ee10.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.ee10.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.ee10.websocket.server.JettyWebSocketCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Accepts websocket connections for the event endpoint up to a maximum number of open
 * connections; beyond it the upgrade is refused with HTTP 503.
 * <p>
 * Every event goes to every open connection, so the cost of each event grows with the number
 * of connections. The check runs before the connection is opened, so connections upgrading at
 * the same moment can overshoot the limit by a few.
 */
public class WebSocketConnectionCreator implements JettyWebSocketCreator {

  static final int SERVICE_UNAVAILABLE = 503;

  private static final Logger logger = LoggerFactory.getLogger(WebSocketConnectionCreator.class);

  private final int maxConnections;
  private final IntSupplier openConnections;
  private final Supplier<Object> socketFactory;

  /**
   * @param maxConnections  the maximum number of open connections, {@code <= 0} for no limit
   * @param openConnections the number of currently open connections
   * @param socketFactory   creates the websocket endpoint for an accepted connection
   */
  public WebSocketConnectionCreator(int maxConnections, IntSupplier openConnections, Supplier<Object> socketFactory) {
    this.maxConnections = maxConnections;
    this.openConnections = openConnections;
    this.socketFactory = socketFactory;
  }

  @Override
  public Object createWebSocket(JettyServerUpgradeRequest request, JettyServerUpgradeResponse response) throws Exception {
    if (maxConnections > 0 && openConnections.getAsInt() >= maxConnections) {
      logger.debug("Refusing websocket connection: limit of {} connections reached", maxConnections);
      response.sendError(SERVICE_UNAVAILABLE, "Too many websocket connections");
      return null;
    }
    return socketFactory.get();
  }
}
