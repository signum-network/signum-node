package brs.http;

import brs.*;
import brs.DigitalGoodsStore.Purchase;
import brs.common.QuickMocker;
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

import static brs.TransactionType.DigitalGoods.FEEDBACK;
import static brs.http.JSONResponses.GOODS_NOT_DELIVERED;
import static brs.http.JSONResponses.INCORRECT_PURCHASE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Burst.class)
public class DGSFeedbackTest extends AbstractTransactionTest {

  private DGSFeedback t;

  private ParameterService parameterServiceMock;
  private AccountService accountServiceMock;
  private Blockchain blockchainMock;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    parameterServiceMock = mock(ParameterService.class);
    accountServiceMock = mock(AccountService.class);
    blockchainMock = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSFeedback(parameterServiceMock, blockchainMock, accountServiceMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws BurstException {
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

    mockStatic(Burst.class);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Burst.getFluxCapacitor()).thenReturn(fluxCapacitor);

    final Attachment.DigitalGoodsFeedback attachment = (Attachment.DigitalGoodsFeedback) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(FEEDBACK, attachment.getTransactionType());
    assertEquals(mockPurchaseId, attachment.getPurchaseId());
  }

  @Test
  public void processRequest_incorrectPurchaseWhenOtherBuyerId() throws BurstException {
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
  public void processRequest_goodsNotDeliveredWhenNoEncryptedGoods() throws BurstException {
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

}
