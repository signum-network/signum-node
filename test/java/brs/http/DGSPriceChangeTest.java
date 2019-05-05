package brs.http;

import brs.*;
import brs.DigitalGoodsStore.Goods;
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

import static brs.TransactionType.DigitalGoods.PRICE_CHANGE;
import static brs.http.JSONResponses.UNKNOWN_GOODS;
import static brs.http.common.Parameters.PRICE_NQT_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Burst.class)
public class DGSPriceChangeTest extends AbstractTransactionTest {

  private DGSPriceChange t;

  private ParameterService parameterServiceMock;
  private Blockchain blockchainMock;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    parameterServiceMock = mock(ParameterService.class);
    blockchainMock = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSPriceChange(parameterServiceMock, blockchainMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws BurstException {
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

    mockStatic(Burst.class);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Burst.getFluxCapacitor()).thenReturn(fluxCapacitor);

    final Attachment.DigitalGoodsPriceChange attachment = (Attachment.DigitalGoodsPriceChange) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(PRICE_CHANGE, attachment.getTransactionType());
    assertEquals(mockGoodsId, attachment.getGoodsId());
    assertEquals(priceNQTParameter, attachment.getPriceNQT());
  }

  @Test
  public void processRequest_goodsDelistedUnknownGoods() throws BurstException {
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
  public void processRequest_goodsWrongSellerIdUnknownGoods() throws BurstException {
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

}
