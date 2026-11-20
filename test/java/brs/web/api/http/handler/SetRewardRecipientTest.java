package brs.web.api.http.handler;

import brs.*;
import brs.common.JSONTestHelper;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.common.TestConstants;
import brs.crypto.Crypto;
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

import static brs.TransactionType.SignaMining.REWARD_RECIPIENT_ASSIGNMENT;
import static brs.web.api.http.common.Parameters.RECIPIENT_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class SetRewardRecipientTest extends AbstractTransactionTest {

  private MockedStatic<Signum> signumMock;

  private SetRewardRecipient t;

  private ParameterService parameterServiceMock;
  private Blockchain blockchainMock;
  private AccountService accountServiceMock;
  private APITransactionManager apiTransactionManagerMock;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    parameterServiceMock = mock(ParameterService.class);
    blockchainMock = mock(Blockchain.class);
    accountServiceMock = mock(AccountService.class);
    apiTransactionManagerMock = mock(APITransactionManager.class);

    t = new SetRewardRecipient(parameterServiceMock, blockchainMock, accountServiceMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(RECIPIENT_PARAMETER, "123"));
    final Account mockSenderAccount = mock(Account.class);
    final Account mockRecipientAccount = mock(Account.class);

    when(mockRecipientAccount.getPublicKey()).thenReturn(Crypto.getPublicKey(TestConstants.TEST_SECRET_PHRASE));

    when(parameterServiceMock.getAccount(eq(req))).thenReturn(mockSenderAccount);
    when(accountServiceMock.getAccount(eq(123L))).thenReturn(mockRecipientAccount);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(Constants.FEE_QUANT_SIP3).when(fluxCapacitor).getValue(eq(FluxValues.FEE_QUANT));

    final Attachment.SignaMiningRewardRecipientAssignment attachment = (Attachment.SignaMiningRewardRecipientAssignment) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(REWARD_RECIPIENT_ASSIGNMENT, attachment.getTransactionType());
  }

  @Test
  public void processRequest_recipientAccountDoesNotExist_errorCode8() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(RECIPIENT_PARAMETER, "123"));
    final Account mockSenderAccount = mock(Account.class);

    when(parameterServiceMock.getAccount(eq(req))).thenReturn(mockSenderAccount);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(false).when(fluxCapacitor).getValue(eq(FluxValues.SMART_TOKEN));

    assertEquals(8, JSONTestHelper.errorCode(t.processRequest(req)));
  }

  @Test
  public void processRequest_recipientAccountDoesNotHavePublicKey_errorCode8() throws SignumException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(RECIPIENT_PARAMETER, "123"));
    final Account mockSenderAccount = mock(Account.class);
    final Account mockRecipientAccount = mock(Account.class);

    when(parameterServiceMock.getAccount(eq(req))).thenReturn(mockSenderAccount);
    when(accountServiceMock.getAccount(eq(123L))).thenReturn(mockRecipientAccount);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Signum.getFluxCapacitor()).thenReturn(fluxCapacitor);
    doReturn(false).when(fluxCapacitor).getValue(eq(FluxValues.SMART_TOKEN));

    assertEquals(8, JSONTestHelper.errorCode(t.processRequest(req)));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
