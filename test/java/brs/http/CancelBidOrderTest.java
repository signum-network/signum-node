package brs.http;

import brs.*;
import brs.Order.Bid;
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

import static brs.TransactionType.ColoredCoins.BID_ORDER_CANCELLATION;
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
public class CancelBidOrderTest extends AbstractTransactionTest {

  private CancelBidOrder t;

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

    t = new CancelBidOrder(parameterServiceMock, blockchainMock, assetExchangeMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws BurstException {
    final int orderId = 123;
    final long orderAccountId = 1;
    final long senderAccountId = orderAccountId;

    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ORDER_PARAMETER, orderId)
    );

    final Bid mockBidOrder = mock(Bid.class);
    when(mockBidOrder.getAccountId()).thenReturn(orderAccountId);
    when(assetExchangeMock.getBidOrder(eq(123L))).thenReturn(mockBidOrder);

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(senderAccountId);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockAccount);

    mockStatic(Burst.class);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Burst.getFluxCapacitor()).thenReturn(fluxCapacitor);

    final Attachment.ColoredCoinsBidOrderCancellation attachment = (brs.Attachment.ColoredCoinsBidOrderCancellation) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(BID_ORDER_CANCELLATION, attachment.getTransactionType());
    assertEquals(orderId, attachment.getOrderId());
  }

  @Test(expected = ParameterException.class)
  public void processRequest_orderParameterMissing() throws BurstException {
    t.processRequest(QuickMocker.httpServletRequest());
  }

  @Test
  public void processRequest_orderDataMissingUnkownOrder() throws BurstException {
    final int orderId = 123;
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ORDER_PARAMETER, orderId)
    );

    when(assetExchangeMock.getBidOrder(eq(123L))).thenReturn(null);

    assertEquals(UNKNOWN_ORDER, t.processRequest(req));
  }

  @Test
  public void processRequest_accountIdNotSameAsOrder() throws BurstException {
    final int orderId = 123;
    final long orderAccountId = 1;
    final long senderAccountId = 2;

    HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(ORDER_PARAMETER, orderId)
    );

    final Bid mockBidOrder = mock(Bid.class);
    when(mockBidOrder.getAccountId()).thenReturn(orderAccountId);
    when(assetExchangeMock.getBidOrder(eq(123L))).thenReturn(mockBidOrder);

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(senderAccountId);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockAccount);

    assertEquals(UNKNOWN_ORDER, t.processRequest(req));
  }

}
