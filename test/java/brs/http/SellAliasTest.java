package brs.http;

import brs.*;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.ParameterService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.http.HttpServletRequest;

import static brs.Constants.MAX_BALANCE_NQT;
import static brs.TransactionType.Messaging.ALIAS_SELL;
import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.PRICE_NQT_PARAMETER;
import static brs.http.common.Parameters.RECIPIENT_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Burst.class)
public class SellAliasTest extends AbstractTransactionTest {

  private SellAlias t;

  private ParameterService parameterServiceMock;
  private Blockchain blockchainMock;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    parameterServiceMock = mock(ParameterService.class);
    blockchainMock = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new SellAlias(parameterServiceMock, blockchainMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws BurstException {
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

    mockStatic(Burst.class);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Burst.getFluxCapacitor()).thenReturn(fluxCapacitor);

    final Attachment.MessagingAliasSell attachment = (Attachment.MessagingAliasSell) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(ALIAS_SELL, attachment.getTransactionType());
    assertEquals(priceParameter, attachment.getPriceNQT());
  }

  @Test
  public void processRequest_missingPrice() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    assertEquals(MISSING_PRICE, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectPrice_unParsable() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
      new MockParam(PRICE_NQT_PARAMETER, "unParsable")
    );

    assertEquals(INCORRECT_PRICE, t.processRequest(req));
  }

  @Test(expected = ParameterException.class)
  public void processRequest_incorrectPrice_negative() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, -10)
    );

    t.processRequest(req);
  }

  @Test(expected = ParameterException.class)
  public void processRequest_incorrectPrice_overMaxBalance() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, MAX_BALANCE_NQT + 1)
    );

    t.processRequest(req);
  }

  @Test
  public void processRequest_incorrectRecipient_unparsable() throws BurstException {
    final int price = 10;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, price),
        new MockParam(RECIPIENT_PARAMETER, "unParsable")
    );

    assertEquals(INCORRECT_RECIPIENT, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectRecipient_zero() throws BurstException {
    final int price = 10;
    final int recipientId = 0;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, price),
        new MockParam(RECIPIENT_PARAMETER, recipientId)
    );

    assertEquals(INCORRECT_RECIPIENT, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectAliasOwner() throws BurstException {
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

}
