package brs.web.api.http.handler;

import brs.*;
import brs.DigitalGoodsStore.Goods;
import brs.common.QuickMocker;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.ParameterService;
import brs.web.api.http.common.APITransactionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;

import static brs.TransactionType.DigitalGoods.DELISTING;
import static brs.web.api.http.common.JSONResponses.UNKNOWN_GOODS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class DGSDelistingTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private DGSDelisting t;

  private ParameterService mockParameterService;
  private Blockchain mockBlockchain;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    mockParameterService = mock(ParameterService.class);
    mockBlockchain = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSDelisting(mockParameterService, mockBlockchain, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockAccount = mock(Account.class);
    final Goods mockGoods = mock(Goods.class);

    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getSellerId()).thenReturn(1L);
    when(mockAccount.getId()).thenReturn(1L);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockAccount);
    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.DigitalGoodsDelisting attachment = (Attachment.DigitalGoodsDelisting) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(DELISTING, attachment.getTransactionType());
    assertEquals(mockGoods.getId(), attachment.getGoodsId());
  }

  @Test
  public void processRequest_goodsDelistedUnknownGoods() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockAccount = mock(Account.class);
    final Goods mockGoods = mock(Goods.class);

    when(mockGoods.isDelisted()).thenReturn(true);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockAccount);
    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(UNKNOWN_GOODS, t.processRequest(req));
  }

  @Test
  public void processRequest_otherSellerIdUnknownGoods() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockAccount = mock(Account.class);
    final Goods mockGoods = mock(Goods.class);

    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getSellerId()).thenReturn(1L);
    when(mockAccount.getId()).thenReturn(2L);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockAccount);
    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(UNKNOWN_GOODS, t.processRequest(req));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
