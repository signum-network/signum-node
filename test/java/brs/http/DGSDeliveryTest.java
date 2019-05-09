package brs.http;

import brs.*;
import brs.DigitalGoodsStore.Purchase;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
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

import static brs.Constants.MAX_BALANCE_NQT;
import static brs.TransactionType.DigitalGoods.DELIVERY;
import static brs.common.TestConstants.TEST_SECRET_PHRASE;
import static brs.http.JSONResponses.*;
import static brs.http.common.Parameters.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Burst.class)
public class DGSDeliveryTest extends AbstractTransactionTest {

  private DGSDelivery t;

  private ParameterService parameterServiceMock;
  private Blockchain blockchainMock;
  private AccountService accountServiceMock;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    parameterServiceMock = mock(ParameterService.class);
    blockchainMock = mock(Blockchain.class);
    accountServiceMock = mock(AccountService.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new DGSDelivery(parameterServiceMock, blockchainMock, accountServiceMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws BurstException {
    final long discountNQTParameter = 1;
    final String goodsToEncryptParameter = "beef";

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(DISCOUNT_NQT_PARAMETER, discountNQTParameter),
        new MockParam(GOODS_TO_ENCRYPT_PARAMETER, goodsToEncryptParameter),
        new MockParam(SECRET_PHRASE_PARAMETER, TEST_SECRET_PHRASE)
    );

    final Account mockSellerAccount = mock(Account.class);
    final Account mockBuyerAccount = mock(Account.class);
    final Purchase mockPurchase = mock(Purchase.class);

    when(mockSellerAccount.getId()).thenReturn(1L);
    when(mockPurchase.getSellerId()).thenReturn(1L);
    when(mockPurchase.getBuyerId()).thenReturn(2L);
    when(mockPurchase.getQuantity()).thenReturn(9);
    when(mockPurchase.getPriceNQT()).thenReturn(1L);

    when(mockPurchase.isPending()).thenReturn(true);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(parameterServiceMock.getPurchase(eq(req))).thenReturn(mockPurchase);
    when(accountServiceMock.getAccount(eq(mockPurchase.getBuyerId()))).thenReturn(mockBuyerAccount);

    mockStatic(Burst.class);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Burst.getFluxCapacitor()).thenReturn(fluxCapacitor);

    final Attachment.DigitalGoodsDelivery attachment = (Attachment.DigitalGoodsDelivery) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(DELIVERY, attachment.getTransactionType());
    assertEquals(discountNQTParameter, attachment.getDiscountNQT());
  }

  @Test
  public void processRequest_sellerAccountIdDifferentFromAccountSellerIdIsIncorrectPurchase() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockSellerAccount = mock(Account.class);
    final Purchase mockPurchase = mock(Purchase.class);

    when(mockSellerAccount.getId()).thenReturn(1L);
    when(mockPurchase.getSellerId()).thenReturn(2L);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(parameterServiceMock.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(INCORRECT_PURCHASE, t.processRequest(req));
  }

  @Test
  public void processRequest_purchaseNotPendingIsAlreadyDelivered() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockSellerAccount = mock(Account.class);
    final Purchase mockPurchase = mock(Purchase.class);

    when(mockSellerAccount.getId()).thenReturn(1L);
    when(mockPurchase.getSellerId()).thenReturn(1L);

    when(mockPurchase.isPending()).thenReturn(false);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(parameterServiceMock.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(ALREADY_DELIVERED, t.processRequest(req));
  }

  @Test
  public void processRequest_dgsDiscountNotAValidNumberIsIncorrectDGSDiscount() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(DISCOUNT_NQT_PARAMETER, "Bob")
    );

    final Account mockSellerAccount = mock(Account.class);
    final Purchase mockPurchase = mock(Purchase.class);

    when(mockSellerAccount.getId()).thenReturn(1L);
    when(mockPurchase.getSellerId()).thenReturn(1L);

    when(mockPurchase.isPending()).thenReturn(true);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(parameterServiceMock.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(INCORRECT_DGS_DISCOUNT, t.processRequest(req));
  }

  @Test
  public void processRequest_dgsDiscountNegativeIsIncorrectDGSDiscount() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(DISCOUNT_NQT_PARAMETER, "-1")
    );

    final Account mockSellerAccount = mock(Account.class);
    final Purchase mockPurchase = mock(Purchase.class);

    when(mockSellerAccount.getId()).thenReturn(1L);
    when(mockPurchase.getSellerId()).thenReturn(1L);

    when(mockPurchase.isPending()).thenReturn(true);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(parameterServiceMock.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(INCORRECT_DGS_DISCOUNT, t.processRequest(req));
  }

  @Test
  public void processRequest_dgsDiscountOverMaxBalanceNQTIsIncorrectDGSDiscount() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(DISCOUNT_NQT_PARAMETER, "" + (MAX_BALANCE_NQT + 1))
    );

    final Account mockSellerAccount = mock(Account.class);
    final Purchase mockPurchase = mock(Purchase.class);

    when(mockSellerAccount.getId()).thenReturn(1L);
    when(mockPurchase.getSellerId()).thenReturn(1L);

    when(mockPurchase.isPending()).thenReturn(true);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(parameterServiceMock.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(INCORRECT_DGS_DISCOUNT, t.processRequest(req));
  }

  @Test
  public void processRequest_dgsDiscountNegativeIsNotSafeMultiply() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(DISCOUNT_NQT_PARAMETER, "99999999999")
    );

    final Account mockSellerAccount = mock(Account.class);
    final Purchase mockPurchase = mock(Purchase.class);

    when(mockSellerAccount.getId()).thenReturn(1L);
    when(mockPurchase.getSellerId()).thenReturn(1L);
    when(mockPurchase.getQuantity()).thenReturn(999999999);
    when(mockPurchase.getPriceNQT()).thenReturn(1L);

    when(mockPurchase.isPending()).thenReturn(true);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(parameterServiceMock.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(INCORRECT_DGS_DISCOUNT, t.processRequest(req));
  }

  @Test
  public void processRequest_goodsToEncryptIsEmptyIsIncorrectDGSGoods() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(DISCOUNT_NQT_PARAMETER, "9"),
        new MockParam(GOODS_TO_ENCRYPT_PARAMETER, ""),
        new MockParam(SECRET_PHRASE_PARAMETER, TEST_SECRET_PHRASE)
    );

    final Account mockSellerAccount = mock(Account.class);
    final Purchase mockPurchase = mock(Purchase.class);

    when(mockSellerAccount.getId()).thenReturn(1L);
    when(mockPurchase.getSellerId()).thenReturn(1L);
    when(mockPurchase.getQuantity()).thenReturn(9);
    when(mockPurchase.getPriceNQT()).thenReturn(1L);

    when(mockPurchase.isPending()).thenReturn(true);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockSellerAccount);
    when(parameterServiceMock.getPurchase(eq(req))).thenReturn(mockPurchase);

    assertEquals(INCORRECT_DGS_GOODS, t.processRequest(req));
  }

}
