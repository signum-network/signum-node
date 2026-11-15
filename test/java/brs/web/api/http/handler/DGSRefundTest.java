package brs.web.api.http.handler;

import brs.*;
import brs.DigitalGoodsStore.Purchase;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.crypto.EncryptedData;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.AccountService;
import brs.services.ParameterService;
import brs.web.api.http.common.APITransactionManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;

import static brs.TransactionType.DigitalGoods.REFUND;
import static brs.web.api.http.common.JSONResponses.*;
import static brs.web.api.http.common.Parameters.REFUND_NQT_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

public class DGSRefundTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private DGSRefund t;

  private ParameterService mockParameterService;
  private Blockchain mockBlockchain;
  private AccountService mockAccountService;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    mockParameterService = mock(ParameterService.class);
    mockBlockchain = mock(Blockchain.class);
    mockAccountService = mock(AccountService.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSRefund(mockParameterService, mockBlockchain, mockAccountService, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final long refundNQTParameter = 5;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(REFUND_NQT_PARAMETER, refundNQTParameter)
    );

    final Account mockSellerAccount = mock(Account.class);
    when(mockSellerAccount.getId()).thenReturn(1L);

    long mockPurchaseId = 123L;
    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.getId()).thenReturn(mockPurchaseId);
    when(mockPurchase.getSellerId()).thenReturn(1L);
    when(mockPurchase.getBuyerId()).thenReturn(2L);
    when(mockPurchase.getRefundNote()).thenReturn(null);
    when(mockPurchase.getEncryptedGoods()).thenReturn(mock(EncryptedData.class));

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(mockParameterService.getPurchase(eq(req))).thenReturn(mockPurchase);

    final Account mockBuyerAccount = mock(Account.class);

    when(mockAccountService.getAccount(eq(mockPurchase.getBuyerId()))).thenReturn(mockBuyerAccount);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.DigitalGoodsRefund attachment = (Attachment.DigitalGoodsRefund) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(REFUND, attachment.getTransactionType());
    assertEquals(refundNQTParameter, attachment.getRefundNqt());
    assertEquals(mockPurchaseId, attachment.getPurchaseId());
  }

  @Test
  public void processRequest_incorrectPurchase() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockSellerAccount = mock(Account.class);
    when(mockSellerAccount.getId()).thenReturn(1L);

    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.getSellerId()).thenReturn(2L);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(mockParameterService.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(INCORRECT_PURCHASE, t.processRequest(req));
  }

  @Test
  public void processRequest_duplicateRefund() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockSellerAccount = mock(Account.class);
    when(mockSellerAccount.getId()).thenReturn(1L);

    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.getSellerId()).thenReturn(1L);
    when(mockPurchase.getRefundNote()).thenReturn(mock(EncryptedData.class));

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(mockParameterService.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(DUPLICATE_REFUND, t.processRequest(req));
  }

  @Test
  public void processRequest_goodsNotDelivered() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockSellerAccount = mock(Account.class);
    when(mockSellerAccount.getId()).thenReturn(1L);

    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.getSellerId()).thenReturn(1L);
    when(mockPurchase.getRefundNote()).thenReturn(null);
    when(mockPurchase.getEncryptedGoods()).thenReturn(null);

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(mockParameterService.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(GOODS_NOT_DELIVERED, t.processRequest(req));
  }

  @Test
  public void processRequest_incorrectDgsRefundWrongFormat() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(REFUND_NQT_PARAMETER, "Bob")
    );

    final Account mockSellerAccount = mock(Account.class);
    when(mockSellerAccount.getId()).thenReturn(1L);

    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.getSellerId()).thenReturn(1L);
    when(mockPurchase.getRefundNote()).thenReturn(null);
    when(mockPurchase.getEncryptedGoods()).thenReturn(mock(EncryptedData.class));

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(mockParameterService.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(INCORRECT_DGS_REFUND, t.processRequest(req));
  }

  @Test
  public void processRequest_negativeIncorrectDGSRefund() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(REFUND_NQT_PARAMETER, -5)
    );

    final Account mockSellerAccount = mock(Account.class);
    when(mockSellerAccount.getId()).thenReturn(1L);

    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.getSellerId()).thenReturn(1L);
    when(mockPurchase.getRefundNote()).thenReturn(null);
    when(mockPurchase.getEncryptedGoods()).thenReturn(mock(EncryptedData.class));

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(mockParameterService.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(INCORRECT_DGS_REFUND, t.processRequest(req));
  }

  @Test
  public void processRequest_overMaxBalanceNQTIncorrectDGSRefund() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(REFUND_NQT_PARAMETER, Constants.MAX_BALANCE_NQT + 1)
    );

    final Account mockSellerAccount = mock(Account.class);
    when(mockSellerAccount.getId()).thenReturn(1L);

    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.getSellerId()).thenReturn(1L);
    when(mockPurchase.getRefundNote()).thenReturn(null);
    when(mockPurchase.getEncryptedGoods()).thenReturn(mock(EncryptedData.class));

    when(mockParameterService.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(mockParameterService.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(INCORRECT_DGS_REFUND, t.processRequest(req));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
