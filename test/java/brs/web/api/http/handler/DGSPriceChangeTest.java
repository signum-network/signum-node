package brs.web.api.http.handler;

import brs.*;
import brs.DigitalGoodsStore.Goods;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.ParameterService;
import brs.web.api.http.common.APITransactionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;

import static brs.TransactionType.DigitalGoods.PRICE_CHANGE;
import static brs.web.api.http.common.JSONResponses.UNKNOWN_GOODS;
import static brs.web.api.http.common.Parameters.PRICE_NQT_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class DGSPriceChangeTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private DGSPriceChange t;

  private ParameterService parameterServiceMock;
  private Blockchain blockchainMock;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    parameterServiceMock = mock(ParameterService.class);
    blockchainMock = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSPriceChange(parameterServiceMock, blockchainMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final int priceNQTParameter = 5;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, priceNQTParameter)
    );

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(1L);

    long mockGoodsId = 123;
    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.getId()).thenReturn(mockGoodsId);
    when(mockGoods.getSellerId()).thenReturn(1L);
    when(mockGoods.isDelisted()).thenReturn(false);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockAccount);
    when(parameterServiceMock.getGoods(eq(req))).thenReturn(mockGoods);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.DigitalGoodsPriceChange attachment = (Attachment.DigitalGoodsPriceChange) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(PRICE_CHANGE, attachment.getTransactionType());
    assertEquals(mockGoodsId, attachment.getGoodsId());
    assertEquals(priceNQTParameter, attachment.getPriceNqt());
  }

  @Test
  public void processRequest_goodsDelistedUnknownGoods() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, 123L)
    );

    final Account mockAccount = mock(Account.class);

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(true);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockAccount);
    when(parameterServiceMock.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(UNKNOWN_GOODS, t.processRequest(req));
  }

  @Test
  public void processRequest_goodsWrongSellerIdUnknownGoods() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(PRICE_NQT_PARAMETER, 123L)
    );

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(1L);

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.getSellerId()).thenReturn(2L);
    when(mockGoods.isDelisted()).thenReturn(false);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockAccount);
    when(parameterServiceMock.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(UNKNOWN_GOODS, t.processRequest(req));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
