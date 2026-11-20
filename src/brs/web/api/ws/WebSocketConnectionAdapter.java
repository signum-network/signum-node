
package brs.web.api.ws;

import brs.web.server.WebServerContext;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketConnectionAdapter extends Session.Listener.Abstract {
  private static final Logger logger = LoggerFactory.getLogger(WebSocketConnectionAdapter.class);
  private WebSocketConnection connection;
  private final BlockchainEventNotifier notifier;

  public WebSocketConnectionAdapter(WebServerContext context) {
    this.notifier = BlockchainEventNotifier.getInstance(context);
  }

  @Override
  public void onWebSocketOpen(Session session) {
    super.onWebSocketOpen(session);
    logger.debug("Endpoint connected: {}", session);
    this.connection = new WebSocketConnection(session);
    this.notifier.addConnection(connection);
  }


  @Override
  public void onWebSocketClose(int statusCode, String reason, Callback callback) {
    logger.debug("Socket Closed: [{}] {}", statusCode, reason);
    if (this.connection != null) {
      this.notifier.removeConnection(this.connection);
    }
    callback.succeed();
  }

  @Override
  public void onWebSocketError(Throwable cause) {
    if (WebSocketErrors.isClientDisconnect(cause)) {
      logger.debug("Socket connection aborted by client: {}", cause.getMessage());
    } else {
      logger.error("Socket Error: {}", cause.getMessage(), cause);
    }
    if (this.connection != null) {
      this.notifier.removeConnection(this.connection);
    }
  }

}
