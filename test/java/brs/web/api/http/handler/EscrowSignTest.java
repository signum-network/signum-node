package brs.web.api.http.handler;

import brs.*;
import brs.Escrow.DecisionType;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.EscrowService;
import brs.services.ParameterService;
import brs.util.JSON;
import brs.web.api.http.common.APITransactionManager;
import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;

import static brs.TransactionType.AdvancedPayment.ESCROW_SIGN;
import static brs.web.api.http.common.Parameters.DECISION_PARAMETER;
import static brs.web.api.http.common.Parameters.ESCROW_PARAMETER;
import static brs.web.api.http.common.ResultFields.ERROR_CODE_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class EscrowSignTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private ParameterService parameterServiceMock;
  private Blockchain blockchainMock;
  private EscrowService escrowServiceMock;
  private APITransactionManager apiTransactionManagerMock;

  private EscrowSign t;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    parameterServiceMock = mock(ParameterService.class);
    blockchainMock = mock(Blockchain.class);
    escrowServiceMock = mock(EscrowService.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new EscrowSign(parameterServiceMock, blockchainMock, escrowServiceMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest_positiveAsEscrowSender() throws SignumException {
    final long escrowId = 5;
    final long senderId = 6;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ESCROW_PARAMETER, escrowId),
        new MockParam(DECISION_PARAMETER, "release")
    );

    final Escrow escrow = mock(Escrow.class);
    when(escrow.getSenderId()).thenReturn(senderId);
    when(escrow.getRecipientId()).thenReturn(2L);

    final Account sender = mock(Account.class);
    when(sender.getId()).thenReturn(senderId);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    when(escrowServiceMock.getEscrowTransaction(eq(escrowId))).thenReturn(escrow);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(sender);

    final Attachment.AdvancedPaymentEscrowSign attachment = (brs.Attachment.AdvancedPaymentEscrowSign) attachmentCreatedTransaction(() -> t.processRequest(req),
        apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(ESCROW_SIGN, attachment.getTransactionType());
    assertEquals(DecisionType.RELEASE, attachment.getDecision());
  }

  @Test
  public void processRequest_positiveAsEscrowRecipient() throws SignumException {
    final long escrowId = 5;
    final long senderId = 6;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ESCROW_PARAMETER, escrowId),
        new MockParam(DECISION_PARAMETER, "refund")
    );

    final Escrow escrow = mock(Escrow.class);
    when(escrow.getSenderId()).thenReturn(1L);
    when(escrow.getRecipientId()).thenReturn(senderId);

    final Account sender = mock(Account.class);
    when(sender.getId()).thenReturn(senderId);

    when(escrowServiceMock.getEscrowTransaction(eq(escrowId))).thenReturn(escrow);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(sender);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.AdvancedPaymentEscrowSign attachment = (brs.Attachment.AdvancedPaymentEscrowSign) attachmentCreatedTransaction(() -> t.processRequest(req),
        apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(ESCROW_SIGN, attachment.getTransactionType());
    assertEquals(DecisionType.REFUND, attachment.getDecision());
  }

  @Test
  public void processRequest_positiveAsEscrowSigner() throws SignumException {
    final long escrowId = 5;
    final long senderId = 6;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ESCROW_PARAMETER, escrowId),
        new MockParam(DECISION_PARAMETER, "refund")
    );

    final Escrow escrow = mock(Escrow.class);
    when(escrow.getRecipientId()).thenReturn(1L);
    when(escrow.getSenderId()).thenReturn(2L);

    final Account sender = mock(Account.class);
    when(sender.getId()).thenReturn(senderId);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    when(escrowServiceMock.isIdSigner(eq(senderId), eq(escrow))).thenReturn(true);

    when(escrowServiceMock.getEscrowTransaction(eq(escrowId))).thenReturn(escrow);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(sender);

    final Attachment.AdvancedPaymentEscrowSign attachment = (brs.Attachment.AdvancedPaymentEscrowSign) attachmentCreatedTransaction(() -> t.processRequest(req),
        apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(ESCROW_SIGN, attachment.getTransactionType());
    assertEquals(DecisionType.REFUND, attachment.getDecision());
  }

  @Test
  public void processRequest_invalidEscrowId() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ESCROW_PARAMETER, "NotANumber")
    );

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertEquals(3, JSON.getAsInt(result.get(ERROR_CODE_RESPONSE)));
  }

  @Test
  public void processRequest_escrowNotFound() throws SignumException {
    final long escrowId = 5;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ESCROW_PARAMETER, escrowId)
    );

    when(escrowServiceMock.getEscrowTransaction(eq(escrowId))).thenReturn(null);

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertEquals(5, JSON.getAsInt(result.get(ERROR_CODE_RESPONSE)));
  }

  @Test
  public void processRequest_invalidDecisionType() throws SignumException {
    final long escrowId = 5;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ESCROW_PARAMETER, escrowId),
        new MockParam(DECISION_PARAMETER, "notADecisionValue")
    );

    final Escrow escrow = mock(Escrow.class);

    when(escrowServiceMock.getEscrowTransaction(eq(escrowId))).thenReturn(escrow);

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertEquals(5, JSON.getAsInt(result.get(ERROR_CODE_RESPONSE)));
  }

  @Test
  public void processRequest_invalidSender() throws SignumException {
    final long escrowId = 5;
    final long senderId = 6;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ESCROW_PARAMETER, escrowId),
        new MockParam(DECISION_PARAMETER, "refund")
    );

    final Escrow escrow = mock(Escrow.class);
    when(escrow.getSenderId()).thenReturn(1L);
    when(escrow.getRecipientId()).thenReturn(2L);

    when(escrowServiceMock.isIdSigner(eq(senderId), eq(escrow))).thenReturn(false);

    final Account sender = mock(Account.class);
    when(sender.getId()).thenReturn(senderId);

    when(escrowServiceMock.getEscrowTransaction(eq(escrowId))).thenReturn(escrow);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(sender);

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertEquals(5, JSON.getAsInt(result.get(ERROR_CODE_RESPONSE)));
  }

  @Test
  public void processRequest_senderCanOnlyRelease() throws SignumException {
    final long escrowId = 5;
    final long senderId = 6;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ESCROW_PARAMETER, escrowId),
        new MockParam(DECISION_PARAMETER, "refund")
    );

    final Escrow escrow = mock(Escrow.class);
    when(escrow.getSenderId()).thenReturn(senderId);

    final Account sender = mock(Account.class);
    when(sender.getId()).thenReturn(senderId);

    when(escrowServiceMock.getEscrowTransaction(eq(escrowId))).thenReturn(escrow);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(sender);

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertEquals(4, JSON.getAsInt(result.get(ERROR_CODE_RESPONSE)));
  }

  @Test
  public void processRequest_recipientCanOnlyRefund() throws SignumException {
    final long escrowId = 5;
    final long senderId = 6;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ESCROW_PARAMETER, escrowId),
        new MockParam(DECISION_PARAMETER, "release")
    );

    final Escrow escrow = mock(Escrow.class);
    when(escrow.getRecipientId()).thenReturn(senderId);

    final Account sender = mock(Account.class);
    when(sender.getId()).thenReturn(senderId);

    when(escrowServiceMock.getEscrowTransaction(eq(escrowId))).thenReturn(escrow);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(sender);

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertEquals(4, JSON.getAsInt(result.get(ERROR_CODE_RESPONSE)));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
