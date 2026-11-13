package brs.web.api.http.handler;

import brs.*;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
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

import static brs.TransactionType.ColoredCoins.ASSET_TRANSFER;
import static brs.web.api.http.common.JSONResponses.NOT_ENOUGH_ASSETS;
import static brs.web.api.http.common.Parameters.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class TransferAssetTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private TransferAsset t;

  private ParameterService parameterServiceMock;
  private Blockchain blockchainMock;
  private APITransactionManager apiTransactionManagerMock;
  private AccountService accountServiceMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    parameterServiceMock = mock(ParameterService.class);
    blockchainMock = mock(Blockchain.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);
    accountServiceMock = mock(AccountService.class);

    t = new TransferAsset(parameterServiceMock, blockchainMock, apiTransactionManagerMock, accountServiceMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final long recipientParameter = 34L;
    final long assetIdParameter = 456L;
    final long quantityQNTParameter = 56L;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(RECIPIENT_PARAMETER, recipientParameter),
        new MockParam(ASSET_PARAMETER, assetIdParameter),
        new MockParam(QUANTITY_QNT_PARAMETER, quantityQNTParameter)
    );

    Asset mockAsset = mock(Asset.class);

    when(parameterServiceMock.getAsset(eq(req))).thenReturn(mockAsset);
    when(mockAsset.getId()).thenReturn(assetIdParameter);

    final Account mockSenderAccount = mock(Account.class);
    when(accountServiceMock.getUnconfirmedAssetBalanceQNT(eq(mockSenderAccount), eq(assetIdParameter))).thenReturn(500L);

    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockSenderAccount);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(ASSET_TRANSFER, attachment.getTransactionType());
    assertEquals(assetIdParameter, attachment.getAssetId());
    assertEquals(quantityQNTParameter, attachment.getQuantityQnt());
  }

  @Test
  public void processRequest_assetBalanceLowerThanQuantityNQTParameter() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(RECIPIENT_PARAMETER, "123"),
        new MockParam(ASSET_PARAMETER, "456"),
        new MockParam(QUANTITY_QNT_PARAMETER, "5")
    );

    Asset mockAsset = mock(Asset.class);

    when(parameterServiceMock.getAsset(eq(req))).thenReturn(mockAsset);
    when(mockAsset.getId()).thenReturn(456l);

    final Account mockSenderAccount = mock(Account.class);
    when(parameterServiceMock.getSenderAccount(eq(req))).thenReturn(mockSenderAccount);

    when(accountServiceMock.getUnconfirmedAssetBalanceQNT(eq(mockSenderAccount), anyLong())).thenReturn(2L);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(false).when(fluxCapacitor).getValue(eq(FluxValues.SMART_TOKEN));

    assertEquals(NOT_ENOUGH_ASSETS, t.processRequest(req));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
