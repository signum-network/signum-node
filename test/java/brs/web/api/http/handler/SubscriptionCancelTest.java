package brs.web.api.http.handler;

import brs.*;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.ParameterService;
import brs.services.SubscriptionService;
import brs.util.JSON;
import brs.web.api.http.common.APITransactionManager;
import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;

import static brs.TransactionType.AdvancedPayment.SUBSCRIPTION_CANCEL;
import static brs.web.api.http.common.Parameters.SUBSCRIPTION_PARAMETER;
import static brs.web.api.http.common.ResultFields.ERROR_CODE_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class SubscriptionCancelTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private SubscriptionCancel t;

  private ParameterService parameterServiceMock;
  private SubscriptionService subscriptionServiceMock;
  private Blockchain blockchainMock;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    parameterServiceMock = mock(ParameterService.class);
    subscriptionServiceMock = mock(SubscriptionService.class);
    blockchainMock = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new SubscriptionCancel(parameterServiceMock, subscriptionServiceMock, blockchainMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final Long subscriptionIdParameter = 123L;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(SUBSCRIPTION_PARAMETER, subscriptionIdParameter)
    );

    final Account mockSender = mock(Account.class);
    when(mockSender.getId()).thenReturn(1L);

    final Subscription mockSubscription = mock(Subscription.class);
    when(mockSubscription.getId()).thenReturn(subscriptionIdParameter);
    when(mockSubscription.getSenderId()).thenReturn(1L);
    when(mockSubscription.getRecipientId()).thenReturn(2L);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockSender);
    when(subscriptionServiceMock.getSubscription(eq(subscriptionIdParameter))).thenReturn(mockSubscription);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.AdvancedPaymentSubscriptionCancel attachment = (Attachment.AdvancedPaymentSubscriptionCancel) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(SUBSCRIPTION_CANCEL, attachment.getTransactionType());
    assertEquals(subscriptionIdParameter, attachment.getSubscriptionId());
  }

  @Test
  public void processRequest_missingSubscriptionParameter() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final JsonObject response = (JsonObject) t.processRequest(req);
    assertNotNull(response);

    assertEquals(3, JSON.getAsInt(response.get(ERROR_CODE_RESPONSE)));
  }

  @Test
  public void processRequest_failedToParseSubscription() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
      new MockParam(SUBSCRIPTION_PARAMETER, "notALong")
    );

    final JsonObject response = (JsonObject) t.processRequest(req);
    assertNotNull(response);

    assertEquals(4, JSON.getAsInt(response.get(ERROR_CODE_RESPONSE)));
  }

  @Test
  public void processRequest_subscriptionNotFound() throws SignumException {
    final long subscriptionId = 123L;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(SUBSCRIPTION_PARAMETER, subscriptionId)
    );

    when(subscriptionServiceMock.getSubscription(eq(subscriptionId))).thenReturn(null);

    final JsonObject response = (JsonObject) t.processRequest(req);
    assertNotNull(response);

    assertEquals(5, JSON.getAsInt(response.get(ERROR_CODE_RESPONSE)));
  }

  @Test
  public void processRequest_userIsNotSenderOrRecipient() throws SignumException {
    final long subscriptionId = 123L;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(SUBSCRIPTION_PARAMETER, subscriptionId)
    );

    final Account mockSender = mock(Account.class);
    when(mockSender.getId()).thenReturn(1L);

    final Subscription mockSubscription = mock(Subscription.class);
    when(mockSubscription.getSenderId()).thenReturn(2L);
    when(mockSubscription.getRecipientId()).thenReturn(3L);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockSender);
    when(subscriptionServiceMock.getSubscription(eq(subscriptionId))).thenReturn(mockSubscription);

    final JsonObject response = (JsonObject) t.processRequest(req);
    assertNotNull(response);

    assertEquals(7, JSON.getAsInt(response.get(ERROR_CODE_RESPONSE)));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
