package brs.http;

import brs.*;
import brs.Order.Ask;
import brs.assetexchange.AssetExchange;
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

import static brs.TransactionType.ColoredCoins.ASK_ORDER_CANCELLATION;
import static brs.http.JSONResponses.UNKNOWN_ORDER;
import static brs.http.common.Parameters.ORDER_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Burst.class)
public class CancelAskOrderTest extends AbstractTransactionTest {

  private CancelAskOrder t;

  private ParameterService parameterServiceMock;
  private Blockchain blockchainMock;
  private AssetExchange assetExchangeMock;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    parameterServiceMock = mock(ParameterService.class);
    blockchainMock = mock(Blockchain.class);
    assetExchangeMock = mock(AssetExchange.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new CancelAskOrder(parameterServiceMock, blockchainMock, assetExchangeMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws BurstException {
    final long orderId = 5;
    final long sellerId = 6;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ORDER_PARAMETER, orderId)
    );

    final Account sellerAccount = mock(Account.class);
    when(sellerAccount.getId()).thenReturn(sellerId);

    final Ask order = mock(Ask.class);
    when(order.getAccountId()).thenReturn(sellerId);

    when(assetExchangeMock.getAskOrder(eq(orderId))).thenReturn(order);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(sellerAccount);

    mockStatic(Burst.class);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Burst.getFluxCapacitor()).thenReturn(fluxCapacitor);

    final Attachment.ColoredCoinsAskOrderCancellation attachment = (brs.Attachment.ColoredCoinsAskOrderCancellation) attachmentCreatedTransaction(() -> t.processRequest(req),
        apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(ASK_ORDER_CANCELLATION, attachment.getTransactionType());
    assertEquals(orderId, attachment.getOrderId());
  }

  @Test
  public void processRequest_orderDataNotFound() throws BurstException {
    int orderId = 5;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ORDER_PARAMETER, orderId)
    );

    when(assetExchangeMock.getAskOrder(eq(orderId))).thenReturn(null);

    assertEquals(UNKNOWN_ORDER, t.processRequest(req));
  }

  @Test
  public void processRequest_orderOtherAccount() throws BurstException {
    final long orderId = 5;
    final long accountId = 6;
    final long otherAccountId = 7;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ORDER_PARAMETER, orderId)
    );

    final Account sellerAccount = mock(Account.class);
    when(sellerAccount.getId()).thenReturn(accountId);

    final Ask order = mock(Ask.class);
    when(order.getAccountId()).thenReturn(otherAccountId);

    when(assetExchangeMock.getAskOrder(eq(orderId))).thenReturn(order);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(sellerAccount);

    assertEquals(UNKNOWN_ORDER, t.processRequest(req));
  }
}
