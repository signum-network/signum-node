package brs.web.api.http.handler;

import brs.*;
import brs.DigitalGoodsStore.Purchase;
import brs.common.QuickMocker;
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

import static brs.TransactionType.DigitalGoods.FEEDBACK;
import static brs.web.api.http.common.JSONResponses.GOODS_NOT_DELIVERED;
import static brs.web.api.http.common.JSONResponses.INCORRECT_PURCHASE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class DGSFeedbackTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private DGSFeedback t;

  private ParameterService parameterServiceMock;
  private AccountService accountServiceMock;
  private Blockchain blockchainMock;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    parameterServiceMock = mock(ParameterService.class);
    accountServiceMock = mock(AccountService.class);
    blockchainMock = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSFeedback(parameterServiceMock, blockchainMock, accountServiceMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final long mockPurchaseId = 123L;
    final Purchase mockPurchase = mock(Purchase.class);
    when(mockPurchase.getId()).thenReturn(mockPurchaseId);
    final Account mockAccount = mock(Account.class);
    final Account mockSellerAccount = mock(Account.class);
    final EncryptedData mockEncryptedGoods = mock(EncryptedData.class);

    when(parameterServiceMock.getPurchase(eq(req))).thenReturn(mockPurchase);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockAccount);
    when(accountServiceMock.getAccount(eq(2L))).thenReturn(mockSellerAccount);

    when(mockAccount.getId()).thenReturn(1L);
    when(mockPurchase.getBuyerId()).thenReturn(1L);
    when(mockPurchase.getEncryptedGoods()).thenReturn(mockEncryptedGoods);
    when(mockPurchase.getSellerId()).thenReturn(2L);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.DigitalGoodsFeedback attachment = (Attachment.DigitalGoodsFeedback) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(FEEDBACK, attachment.getTransactionType());
    assertEquals(mockPurchaseId, attachment.getPurchaseId());
  }

  @Test
  public void processRequest_incorrectPurchaseWhenOtherBuyerId() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Purchase mockPurchase = mock(Purchase.class);
    final Account mockAccount = mock(Account.class);

    when(parameterServiceMock.getPurchase(eq(req))).thenReturn(mockPurchase);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockAccount);

    when(mockAccount.getId()).thenReturn(1L);
    when(mockPurchase.getBuyerId()).thenReturn(2L);

    assertEquals(INCORRECT_PURCHASE, t.processRequest(req));
  }

  @Test
  public void processRequest_goodsNotDeliveredWhenNoEncryptedGoods() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Purchase mockPurchase = mock(Purchase.class);
    final Account mockAccount = mock(Account.class);

    when(parameterServiceMock.getPurchase(eq(req))).thenReturn(mockPurchase);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockAccount);

    when(mockAccount.getId()).thenReturn(1L);
    when(mockPurchase.getBuyerId()).thenReturn(1L);
    when(mockPurchase.getEncryptedGoods()).thenReturn(null);

    assertEquals(GOODS_NOT_DELIVERED, t.processRequest(req));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
