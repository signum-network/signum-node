package brs.http;

import brs.util.JSON;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.http.JSONResponses.MISSING_SIGNATURE_HASH;
import static brs.http.JSONResponses.MISSING_UNSIGNED_BYTES;
import static brs.http.common.Parameters.SIGNATURE_HASH_PARAMETER;
import static brs.http.common.Parameters.UNSIGNED_TRANSACTION_BYTES_PARAMETER;
import static brs.http.common.ResultFields.FULL_HASH_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

public class CalculateFullHashTest {

  private CalculateFullHash t;

  @Before
  public void setUp() {
    t = new CalculateFullHash();
  }

  @Test
  public void processRequest() {
    //TODO More sensible values here...
    final String mockUnsignedTransactionBytes = "123";
    final String mockSignatureHash = "123";
    final String expectedFullHash = "fe09cbf95619345cde91e0dee049d55498085a152e19c1009cb8973f9e1b4518";

    final HttpServletRequest req = mock(HttpServletRequest.class);

    when(req.getParameter(eq(UNSIGNED_TRANSACTION_BYTES_PARAMETER))).thenReturn(mockUnsignedTransactionBytes);
    when(req.getParameter(eq(SIGNATURE_HASH_PARAMETER))).thenReturn(mockSignatureHash);

    final JsonObject result = JSON.getAsJsonObject(t.processRequest(req));
    assertEquals(expectedFullHash, JSON.getAsString(result.get(FULL_HASH_RESPONSE)));
  }

  @Test
  public void processRequest_missingUnsignedBytes() {
    assertEquals(MISSING_UNSIGNED_BYTES, t.processRequest(mock(HttpServletRequest.class)));
  }

  @Test
  public void processRequest_missingSignatureHash() {
    final String mockUnsignedTransactionBytes = "mockUnsignedTransactionBytes";
    final HttpServletRequest req = mock(HttpServletRequest.class);

    when(req.getParameter(eq(UNSIGNED_TRANSACTION_BYTES_PARAMETER))).thenReturn(mockUnsignedTransactionBytes);

    assertEquals(MISSING_SIGNATURE_HASH, t.processRequest(req));
  }
}
