package brs.web.api.http.handler;

import brs.*;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.ParameterService;
import brs.web.api.http.common.APITransactionManager;
import brs.web.api.http.common.ParameterException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;

import static brs.Constants.MAX_BALANCE_NQT;
import static brs.TransactionType.Messaging.ALIAS_SELL;
import static brs.web.api.http.common.JSONResponses.*;
import static brs.web.api.http.common.Parameters.PRICE_NQT_PARAMETER;
import static brs.web.api.http.common.Parameters.RECIPIENT_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class SellAliasTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private SellAlias t;

  private ParameterService parameterServiceMock;
  private Blockchain blockchainMock;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    parameterServiceMock = mock(ParameterService.class);
    blockchainMock = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new SellAlias(parameterServiceMock, blockchainMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final int priceParameter = 10;
    final int recipientId = 5;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, priceParameter),
        new MockParam(RECIPIENT_PARAMETER, recipientId)
    );

    final long aliasAccountId = 1L;
    final Alias mockAlias = mock(Alias.class);
    when(mockAlias.getAccountId()).thenReturn(aliasAccountId);

    final Account mockSender = mock(Account.class);
    when(mockSender.getId()).thenReturn(aliasAccountId);

    when(parameterServiceMock.getSenderAccount(req)).thenReturn(mockSender);
    when(parameterServiceMock.getAlias(req)).thenReturn(mockAlias);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.MessagingAliasSell attachment = (Attachment.MessagingAliasSell) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(ALIAS_SELL, attachment.getTransactionType());
    assertEquals(priceParameter, attachment.getPriceNqt());
  }

  @Test
  public void processRequest_missingPrice() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    assertEquals(MISSING_PRICE, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectPrice_unParsable() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
      new MockParam(PRICE_NQT_PARAMETER, "unParsable")
    );

    assertEquals(INCORRECT_PRICE, t.processRequest(req));
  }

  @Test(expected = ParameterException.class)
  public void processRequest_incorrectPrice_negative() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, -10)
    );

    t.processRequest(req);
  }

  @Test(expected = ParameterException.class)
  public void processRequest_incorrectPrice_overMaxBalance() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, MAX_BALANCE_NQT + 1)
    );

    t.processRequest(req);
  }

  @Test
  public void processRequest_incorrectRecipient_unparsable() throws SignumException {
    final int price = 10;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, price),
        new MockParam(RECIPIENT_PARAMETER, "unParsable")
    );

    assertEquals(INCORRECT_RECIPIENT, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectRecipient_zero() throws SignumException {
    final int price = 10;
    final int recipientId = 0;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, price),
        new MockParam(RECIPIENT_PARAMETER, recipientId)
    );

    assertEquals(INCORRECT_RECIPIENT, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectAliasOwner() throws SignumException {
    final int price = 10;
    final int recipientId = 5;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, price),
        new MockParam(RECIPIENT_PARAMETER, recipientId)
    );

    final long aliasAccountId = 1L;
    final Alias mockAlias = mock(Alias.class);
    when(mockAlias.getAccountId()).thenReturn(aliasAccountId);

    final long mockSenderId = 2l;
    final Account mockSender = mock(Account.class);
    when(mockSender.getId()).thenReturn(mockSenderId);

    when(parameterServiceMock.getSenderAccount(req)).thenReturn(mockSender);
    when(parameterServiceMock.getAlias(req)).thenReturn(mockAlias);

    assertEquals(INCORRECT_ALIAS_OWNER, t.processRequest(req));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
