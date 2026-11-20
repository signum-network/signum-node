package brs.web.api.ws;

import org.eclipse.jetty.io.EofException;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.Locale;

/**
 * Helper to classify WebSocket errors.
 */
final class WebSocketErrors {

  private static final String[] DISCONNECT_MESSAGES = {"broken pipe", "connection reset", "closed"};

  private WebSocketErrors() {
  }

  /**
   * Checks whether the given error (or one of its causes) just signals that the remote peer went away,
   * which is expected behavior for WebSocket clients and no reason for alarm.
   *
   * @param error the error to check, may be null
   * @return true if the error is a client disconnect
   */
  static boolean isClientDisconnect(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
      if (t instanceof EofException || t instanceof ClosedChannelException) {
        return true;
      }
      if (t instanceof IOException && hasDisconnectMessage(t.getMessage())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasDisconnectMessage(String message) {
    if (message == null) {
      return false;
    }
    String lower = message.toLowerCase(Locale.ROOT);
    for (String m : DISCONNECT_MESSAGES) {
      if (lower.contains(m)) {
        return true;
      }
    }
    return false;
  }
}
