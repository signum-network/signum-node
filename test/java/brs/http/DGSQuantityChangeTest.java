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

import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.DELTA_QUANTITY_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Burst.class)
public class DGSQuantityChangeTest extends AbstractTransactionTest {

  private DGSQuantityChange t;

  private ParameterService mockParameterService;
  private Blockchain mockBlockchain;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);
    mockBlockchain = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSQuantityChange(mockParameterService, mockBlockchain, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws BurstException {
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

    mockStatic(Burst.class);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Burst.getFluxCapacitor()).thenReturn(fluxCapacitor);

    final Attachment.DigitalGoodsQuantityChange attachment = (Attachment.DigitalGoodsQuantityChange) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    attachment.getTransactionType();
    assertEquals(mockGoodsID, attachment.getGoodsId());
    assertEquals(deltaQualityParameter, attachment.getDeltaQuantity());
  }

  @Test
  public void processRequest_unknownGoodsBecauseDelisted() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Goods mockGoods = mock(Goods.class);
    when(mockGoods.isDelisted()).thenReturn(true);

    final Account mockSenderAccount = mock(Account.class);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSenderAccount);
    when(mockParameterService.getGoods(eq(req))).thenReturn(mockGoods);

    assertEquals(UNKNOWN_GOODS, t.processRequest(req));
  }

  @Test
  public void processRequest_unknownGoodsBecauseWrongSellerId() throws BurstException {
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
  public void processRequest_missingDeltaQuantity() throws BurstException {
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
  public void processRequest_deltaQuantityWrongFormat() throws BurstException {
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
  public void processRequest_deltaQuantityOverMaxIncorrectDeltaQuantity() throws BurstException {
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
  public void processRequest_deltaQuantityLowerThanNegativeMaxIncorrectDeltaQuantity() throws BurstException {
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

}
