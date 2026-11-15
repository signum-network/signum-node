package brs.web.api.http.handler;

import brs.*;
import brs.DigitalGoodsStore.Goods;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.AccountService;
import brs.services.ParameterService;
import brs.services.TimeService;
import brs.web.api.http.common.APITransactionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;

import static brs.TransactionType.DigitalGoods.PURCHASE;
import static brs.web.api.http.common.JSONResponses.*;
import static brs.web.api.http.common.Parameters.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class DGSPurchaseTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private DGSPurchase t;

  private ParameterService mockParameterService;
  private Blockchain mockBlockchain;
  private AccountService mockAccountService;
  private TimeService mockTimeService;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    mockParameterService = mock(ParameterService.class);
    mockBlockchain = mock(Blockchain.class);
    mockAccountService = mock(AccountService.class);
    mockTimeService = mock(TimeService.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSPurchase(mockParameterService, mockBlockchain, mockAccountService, mockTimeService, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final int goodsQuantity = 5;
    final long goodsPrice = 10L;
    final long deliveryDeadlineTimestamp = 100;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(QUANTITY_PARAMETER, goodsQuantity),
        new MockParam(PRICE_NQT_PARAMETER, goodsPrice),
        new MockParam(DELIVERY_DEADLINE_TIMESTAMP_PARAMETER, deliveryDeadlineTimestamp)
    );

    final long mockSellerId = 123L;
    final long mockGoodsId = 123L;
    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.getId()).thenReturn(mockGoodsId);
    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getQuantity()).thenReturn(10);
    when(mockGoods.getPriceNQT()).thenReturn(10L);
    when(mockGoods.getSellerId()).thenReturn(mockSellerId);

    final Account mockSellerAccount = mock(Account.class);

    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);
    when(mockTimeService.getEpochTime()).thenReturn(10);

    when(mockAccountService.getAccount(eq(mockSellerId))).thenReturn(mockSellerAccount);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.DigitalGoodsPurchase attachment = (Attachment.DigitalGoodsPurchase) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(PURCHASE, attachment.getTransactionType());
    assertEquals(goodsQuantity, attachment.getQuantity());
    assertEquals(goodsPrice, attachment.getPriceNqt());
    assertEquals(deliveryDeadlineTimestamp, attachment.getDeliveryDeadlineTimestamp());
    assertEquals(mockGoodsId, attachment.getGoodsId());
  }

  @Test
  public void processRequest_unknownGoods() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(true);

    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(UNKNOWN_GOODS, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectPurchaseQuantity() throws SignumException {
    final int goodsQuantity = 5;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(QUANTITY_PARAMETER, goodsQuantity)
    );

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getQuantity()).thenReturn(4);

    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(INCORRECT_PURCHASE_QUANTITY, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectPurchasePrice() throws SignumException {
    final int goodsQuantity = 5;
    final long goodsPrice = 5L;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(QUANTITY_PARAMETER, goodsQuantity),
        new MockParam(PRICE_NQT_PARAMETER, goodsPrice)
    );

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getQuantity()).thenReturn(10);
    when(mockGoods.getPriceNQT()).thenReturn(10L);

    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(INCORRECT_PURCHASE_PRICE, t.processRequest(req));
  }


  @Test
  public void processRequest_missingDeliveryDeadlineTimestamp() throws SignumException {
    final int goodsQuantity = 5;
    final long goodsPrice = 10L;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(QUANTITY_PARAMETER, goodsQuantity),
        new MockParam(PRICE_NQT_PARAMETER, goodsPrice)
    );

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getQuantity()).thenReturn(10);
    when(mockGoods.getPriceNQT()).thenReturn(10L);

    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(MISSING_DELIVERY_DEADLINE_TIMESTAMP, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectDeliveryDeadlineTimestamp_unParsable() throws SignumException {
    final int goodsQuantity = 5;
    final long goodsPrice = 10L;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(QUANTITY_PARAMETER, goodsQuantity),
        new MockParam(PRICE_NQT_PARAMETER, goodsPrice),
        new MockParam(DELIVERY_DEADLINE_TIMESTAMP_PARAMETER, "unParsable")
    );

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getQuantity()).thenReturn(10);
    when(mockGoods.getPriceNQT()).thenReturn(10L);

    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(INCORRECT_DELIVERY_DEADLINE_TIMESTAMP, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectDeliveryDeadlineTimestamp_beforeCurrentTime() throws SignumException {
    final int goodsQuantity = 5;
    final long goodsPrice = 10L;
    final long deliveryDeadlineTimestamp = 100;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(QUANTITY_PARAMETER, goodsQuantity),
        new MockParam(PRICE_NQT_PARAMETER, goodsPrice),
        new MockParam(DELIVERY_DEADLINE_TIMESTAMP_PARAMETER, deliveryDeadlineTimestamp)
    );

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getQuantity()).thenReturn(10);
    when(mockGoods.getPriceNQT()).thenReturn(10L);

    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);
    when(mockTimeService.getEpochTime()).thenReturn(1000);

    assertEquals(INCORRECT_DELIVERY_DEADLINE_TIMESTAMP, t.processRequest(req));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
