package brs.web.api.http.handler;

import brs.*;
import brs.Alias.Offer;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.AliasService;
import brs.services.ParameterService;
import brs.web.api.http.common.APITransactionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;

import static brs.TransactionType.Messaging.ALIAS_BUY;
import static brs.web.api.http.common.JSONResponses.INCORRECT_ALIAS_NOTFORSALE;
import static brs.web.api.http.common.Parameters.AMOUNT_NQT_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class BuyAliasTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private BuyAlias t;

  private ParameterService parameterServiceMock;
  private Blockchain blockchain;
  private AliasService aliasService;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void init() {
    signumMock = mockStatic(Signum.class);
    parameterServiceMock = mock(ParameterService.class);
    blockchain = mock(Blockchain.class);
    aliasService = mock(AliasService.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new BuyAlias(parameterServiceMock, blockchain, aliasService, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequestDefaultKeys(new MockParam(AMOUNT_NQT_PARAMETER, "" + Constants.ONE_SIGNA));

    final Offer mockOfferOnAlias = mock(Offer.class);

    final String mockAliasName = "mockAliasName";
    final Alias mockAlias = mock(Alias.class);
    final long mockSellerId = 123L;

    when(mockAlias.getAccountId()).thenReturn(mockSellerId);
    when(mockAlias.getAliasName()).thenReturn(mockAliasName);

    when(aliasService.getOffer(eq(mockAlias))).thenReturn(mockOfferOnAlias);

    when(parameterServiceMock.getAlias(eq(req))).thenReturn(mockAlias);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.MessagingAliasBuy attachment = (Attachment.MessagingAliasBuy) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(ALIAS_BUY, attachment.getTransactionType());
    assertEquals(mockAliasName, attachment.getAliasName());
  }

  @Test
  public void processRequest_aliasNotForSale() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(AMOUNT_NQT_PARAMETER, "3"));
    final Alias mockAlias = mock(Alias.class);

    when(parameterServiceMock.getAlias(eq(req))).thenReturn(mockAlias);

    when(aliasService.getOffer(eq(mockAlias))).thenReturn(null);

    assertEquals(INCORRECT_ALIAS_NOTFORSALE, t.processRequest(req));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
