package brs.web.api.ws;

import org.eclipse.jetty.io.EofException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketErrorsTest {

  @Test
  void isClientDisconnect_GivenEofException_ReturnsTrue() {
    assertTrue(WebSocketErrors.isClientDisconnect(new EofException("Broken pipe")));
  }

  @Test
  void isClientDisconnect_GivenClosedChannelException_ReturnsTrue() {
    assertTrue(WebSocketErrors.isClientDisconnect(new ClosedChannelException()));
  }

  @Test
  void isClientDisconnect_GivenWrappedBrokenPipe_ReturnsTrue() {
    Throwable error = new RuntimeException("wrapper", new IOException("Broken pipe"));
    assertTrue(WebSocketErrors.isClientDisconnect(error));
  }

  @Test
  void isClientDisconnect_GivenConnectionReset_ReturnsTrue() {
    assertTrue(WebSocketErrors.isClientDisconnect(new IOException("Connection reset by peer")));
  }

  @Test
  void isClientDisconnect_GivenClosedMessage_ReturnsTrue() {
    assertTrue(WebSocketErrors.isClientDisconnect(new IOException("Closed")));
  }

  @Test
  void isClientDisconnect_GivenUnrelatedIOException_ReturnsFalse() {
    assertFalse(WebSocketErrors.isClientDisconnect(new IOException("Disk full")));
  }

  @Test
  void isClientDisconnect_GivenRuntimeExceptionWithDisconnectMessage_ReturnsFalse() {
    assertFalse(WebSocketErrors.isClientDisconnect(new IllegalStateException("Broken pipe")));
  }

  @Test
  void isClientDisconnect_GivenIOExceptionWithNullMessage_ReturnsFalse() {
    assertFalse(WebSocketErrors.isClientDisconnect(new IOException((String) null)));
  }

  @Test
  void isClientDisconnect_GivenNull_ReturnsFalse() {
    assertFalse(WebSocketErrors.isClientDisconnect(null));
  }
}
