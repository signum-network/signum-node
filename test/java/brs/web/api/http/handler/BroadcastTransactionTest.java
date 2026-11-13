package brs.web.api.http.handler;

import brs.Signum;
import brs.SignumException;
import brs.Transaction;
import brs.TransactionProcessor;
import brs.common.QuickMocker;
import brs.fluxcapacitor.FluxCapacitor;
import brs.services.ParameterService;
import brs.services.TransactionService;
import brs.util.JSON;
import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;

import static brs.web.api.http.common.Parameters.TRANSACTION_BYTES_PARAMETER;
import static brs.web.api.http.common.Parameters.TRANSACTION_JSON_PARAMETER;
import static brs.web.api.http.common.ResultFields.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class BroadcastTransactionTest {

  private MockedStatic<Signum> signumMock;

  private BroadcastTransaction t;

  private TransactionProcessor transactionProcessorMock;
  private ParameterService parameterServiceMock;
  private TransactionService transactionServiceMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    this.transactionProcessorMock = mock(TransactionProcessor.class);
    this.parameterServiceMock = mock(ParameterService.class);
    this.transactionServiceMock = mock(TransactionService.class);
    FluxCapacitor mockFluxCapacitor = QuickMocker.latestValueFluxCapacitor();
    when(Signum.getFluxCapacitor()).thenReturn(mockFluxCapacitor);

    t = new BroadcastTransaction(transactionProcessorMock, parameterServiceMock, transactionServiceMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final String mockTransactionBytesParameter = "mockTransactionBytesParameter";
    final String mockTransactionJson = "mockTransactionJson";

    final String mockTransactionStringId = "mockTransactionStringId";
    final String mockTransactionFullHash = "mockTransactionFullHash";

    final HttpServletRequest req = mock(HttpServletRequest.class);
    final Transaction mockTransaction = mock(Transaction.class);

    when(mockTransaction.getStringId()).thenReturn(mockTransactionStringId);
    when(mockTransaction.getFullHash()).thenReturn(mockTransactionFullHash);

    when(req.getParameter(TRANSACTION_BYTES_PARAMETER)).thenReturn(mockTransactionBytesParameter);
    when(req.getParameter(TRANSACTION_JSON_PARAMETER)).thenReturn(mockTransactionJson);

    when(parameterServiceMock.parseTransaction(eq(mockTransactionBytesParameter), eq(mockTransactionJson))).thenReturn(mockTransaction);

    final JsonObject result = (JsonObject) t.processRequest(req);

    verify(transactionProcessorMock).broadcast(eq(mockTransaction));

    assertEquals(mockTransactionStringId, JSON.getAsString(result.get(TRANSACTION_RESPONSE)));
    assertEquals(mockTransactionFullHash, JSON.getAsString(result.get(FULL_HASH_RESPONSE)));
  }

  @Test
  public void processRequest_validationException() throws SignumException {
    final String mockTransactionBytesParameter = "mockTransactionBytesParameter";
    final String mockTransactionJson = "mockTransactionJson";

    final HttpServletRequest req = mock(HttpServletRequest.class);
    final Transaction mockTransaction = mock(Transaction.class);

    when(req.getParameter(TRANSACTION_BYTES_PARAMETER)).thenReturn(mockTransactionBytesParameter);
    when(req.getParameter(TRANSACTION_JSON_PARAMETER)).thenReturn(mockTransactionJson);

    when(parameterServiceMock.parseTransaction(eq(mockTransactionBytesParameter), eq(mockTransactionJson))).thenReturn(mockTransaction);

    Mockito.doThrow(SignumException.NotCurrentlyValidException.class).when(transactionServiceMock).validate(eq(mockTransaction));

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertEquals(4, JSON.getAsInt(result.get(ERROR_CODE_RESPONSE)));
    assertNotNull(result.get(ERROR_DESCRIPTION_RESPONSE));
  }

  @Test
  public void requirePost() {
    assertTrue(t.requirePost());
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
