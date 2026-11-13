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

import static brs.web.api.http.common.JSONResponses.*;
import static brs.web.api.http.common.Parameters.DELTA_QUANTITY_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class DGSQuantityChangeTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private DGSQuantityChange t;

  private ParameterService mockParameterService;
  private Blockchain mockBlockchain;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    mockParameterService = mock(ParameterService.class);
    mockBlockchain = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSQuantityChange(mockParameterService, mockBlockchain, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final int deltaQualityParameter = 5;
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(DELTA_QUANTITY_PARAMETER, deltaQualityParameter)
    );

    final long mockGoodsID = 123l;
    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.getId()).thenReturn(mockGoodsID);
    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getSellerId()).thenReturn(1L);

    final Account mockSenderAccount = mock(Account.class);
    when(mockSenderAccount.getId()).thenReturn(1L);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSenderAccount);
    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.DigitalGoodsQuantityChange attachment = (Attachment.DigitalGoodsQuantityChange) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    attachment.getTransactionType();
    assertEquals(mockGoodsID, attachment.getGoodsId());
    assertEquals(deltaQualityParameter, attachment.getDeltaQuantity());
  }

  @Test
  public void processRequest_unknownGoodsBecauseDelisted() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(true);

    final Account mockSenderAccount = mock(Account.class);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSenderAccount);
    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(UNKNOWN_GOODS, t.processRequest(req));
  }

  @Test
  public void processRequest_unknownGoodsBecauseWrongSellerId() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getSellerId()).thenReturn(1L);

    final Account mockSenderAccount = mock(Account.class);
    when(mockSenderAccount.getId()).thenReturn(2L);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSenderAccount);
    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(UNKNOWN_GOODS, t.processRequest(req));
  }

  @Test
  public void processRequest_missingDeltaQuantity() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(DELTA_QUANTITY_PARAMETER, null)
    );

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getSellerId()).thenReturn(1L);

    final Account mockSenderAccount = mock(Account.class);
    when(mockSenderAccount.getId()).thenReturn(1L);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSenderAccount);
    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(MISSING_DELTA_QUANTITY, t.processRequest(req));
  }

  @Test
  public void processRequest_deltaQuantityWrongFormat() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(DELTA_QUANTITY_PARAMETER, "Bob")
    );

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getSellerId()).thenReturn(1L);

    final Account mockSenderAccount = mock(Account.class);
    when(mockSenderAccount.getId()).thenReturn(1L);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSenderAccount);
    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(INCORRECT_DELTA_QUANTITY, t.processRequest(req));
  }

  @Test
  public void processRequest_deltaQuantityOverMaxIncorrectDeltaQuantity() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(DELTA_QUANTITY_PARAMETER, Integer.MIN_VALUE)
    );

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getSellerId()).thenReturn(1L);

    final Account mockSenderAccount = mock(Account.class);
    when(mockSenderAccount.getId()).thenReturn(1L);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSenderAccount);
    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(INCORRECT_DELTA_QUANTITY, t.processRequest(req));
  }

  @Test
  public void processRequest_deltaQuantityLowerThanNegativeMaxIncorrectDeltaQuantity() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(DELTA_QUANTITY_PARAMETER, Integer.MAX_VALUE)
    );

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(false);
    when(mockGoods.getSellerId()).thenReturn(1L);

    final Account mockSenderAccount = mock(Account.class);
    when(mockSenderAccount.getId()).thenReturn(1L);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSenderAccount);
    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(INCORRECT_DELTA_QUANTITY, t.processRequest(req));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
