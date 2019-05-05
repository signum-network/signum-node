package brs.http;

import brs.*;
import brs.DigitalGoodsStore.Purchase;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.crypto.EncryptedData;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.services.AccountService;
import brs.services.ParameterService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.http.HttpServletRequest;

import static brs.TransactionType.DigitalGoods.REFUND;
import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.REFUND_NQT_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Burst.class)
public class DGSRefundTest extends AbstractTransactionTest {

  private DGSRefund t;

  private ParameterService mockParameterService;
  private Blockchain mockBlockchain;
  private AccountService mockAccountService;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);
    mockBlockchain = mock(Blockchain.class);
    mockAccountService = mock(AccountService.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSRefund(mockParameterService, mockBlockchain, mockAccountService, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws BurstException {
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

    mockStatic(Burst.class);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Burst.getFluxCapacitor()).thenReturn(fluxCapacitor);

    final Attachment.DigitalGoodsRefund attachment = (Attachment.DigitalGoodsRefund) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(REFUND, attachment.getTransactionType());
    assertEquals(refundNQTParameter, attachment.getRefundNQT());
    assertEquals(mockPurchaseId, attachment.getPurchaseId());
  }

  @Test
  public void processRequest_incorrectPurchase() throws BurstException {
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
  public void processRequest_duplicateRefund() throws BurstException {
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
  public void processRequest_goodsNotDelivered() throws BurstException {
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
  public void processRequest_incorrectDgsRefundWrongFormat() throws BurstException {
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
  public void processRequest_negativeIncorrectDGSRefund() throws BurstException {
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
  public void processRequest_overMaxBalanceNQTIncorrectDGSRefund() throws BurstException {
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

}
