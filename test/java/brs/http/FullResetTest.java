package brs.http;

import brs.BlockchainProcessor;
import brs.common.QuickMocker;
import brs.util.JSON;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.ResultFields.DONE_RESPONSE;
import static brs.http.common.ResultFields.ERROR_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class FullResetTest {

  private FullReset t;

  private BlockchainProcessor blockchainProcessor;

  @Before
  public void init() {
    blockchainProcessor = mock(BlockchainProcessor.class);

    this.t = new FullReset(blockchainProcessor);
  }

  @Test
  public void processRequest() {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final JsonObject result = ((JsonObject) t.processRequest(req));

    assertTrue(JSON.getAsBoolean(result.get(DONE_RESPONSE)));
  }

  @Test
  public void processRequest_runtimeExceptionOccurs() {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    doThrow(new RuntimeException("errorMessage")).when(blockchainProcessor).fullReset();

    final JsonObject result = ((JsonObject) t.processRequest(req));

    assertEquals("java.lang.RuntimeException: errorMessage", JSON.getAsString(result.get(ERROR_RESPONSE)));
  }

  @Test
  public void requirePost() {
    assertTrue(t.requirePost());
  }
}