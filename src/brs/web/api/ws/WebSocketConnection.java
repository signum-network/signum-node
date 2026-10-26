package brs.web.api.ws;

import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;

public class WebSocketConnection {
  private static final Logger logger = LoggerFactory.getLogger(WebSocketConnection.class);

  private final Session session;

  public WebSocketConnection(Session session) {
    this.session = session;
  }

  public String getId() {
    SocketAddress remoteAddress = session.getRemoteSocketAddress();
    return remoteAddress != null ? remoteAddress.toString() : "unknown";
  }

  public Session getSession() {
    return session;
  }

  public void sendMessage(String message) {
    if (!session.isOpen()) {
      logger.debug("Skipping message send to {} because session is closed", getId());
      return;
    }

    SocketAddress remoteAddress = session.getRemoteSocketAddress();
    session.sendText(message, Callback.from(
      () -> logger.trace("Sent message to {}", remoteAddress),
      throwable -> logSendError(remoteAddress, throwable)
    ));
  }

  private static void logSendError(SocketAddress remoteAddress, Throwable throwable) {
    if (WebSocketErrors.isClientDisconnect(throwable)) {
      logger.debug("Could not send message to {}, connection aborted: {}", remoteAddress, throwable.getMessage());
    } else {
      logger.warn("Error sending message to {}: {}", remoteAddress, throwable.getMessage(), throwable);
    }
  }

  public void close() {
    session.close();
  }
}
