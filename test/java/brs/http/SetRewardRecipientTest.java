package brs.http;

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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.http.HttpServletRequest;

import static brs.TransactionType.BurstMining.REWARD_RECIPIENT_ASSIGNMENT;
import static brs.http.common.Parameters.RECIPIENT_PARAMETER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Burst.class)
public class SetRewardRecipientTest extends AbstractTransactionTest {

  private SetRewardRecipient t;

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

    t = new SetRewardRecipient(parameterServiceMock, blockchainMock, accountServiceMock, apiTransactionManagerMock);
  }

  @Test
  public void processRequest() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(RECIPIENT_PARAMETER, "123"));
    final Account mockSenderAccount = mock(Account.class);
    final Account mockRecipientAccount = mock(Account.class);

    when(mockRecipientAccount.getPublicKey()).thenReturn(Crypto.getPublicKey(TestConstants.TEST_SECRET_PHRASE));

    when(parameterServiceMock.getAccount(eq(req))).thenReturn(mockSenderAccount);
    when(accountServiceMock.getAccount(eq(123L))).thenReturn(mockRecipientAccount);

    mockStatic(Burst.class);
    final FluxCapacitor fluxCapacitor = QuickMocker.fluxCapacitorEnabledFunctionalities(FluxValues.DIGITAL_GOODS_STORE);
    when(Burst.getFluxCapacitor()).thenReturn(fluxCapacitor);

    final Attachment.BurstMiningRewardRecipientAssignment attachment = (Attachment.BurstMiningRewardRecipientAssignment) attachmentCreatedTransaction(() -> t.processRequest(req), apiTransactionManagerMock);
    assertNotNull(attachment);

    assertEquals(REWARD_RECIPIENT_ASSIGNMENT, attachment.getTransactionType());
  }

  @Test
  public void processRequest_recipientAccountDoesNotExist_errorCode8() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(RECIPIENT_PARAMETER, "123"));
    final Account mockSenderAccount = mock(Account.class);

    when(parameterServiceMock.getAccount(eq(req))).thenReturn(mockSenderAccount);

    assertEquals(8, JSONTestHelper.errorCode(t.processRequest(req)));
  }

  @Test
  public void processRequest_recipientAccountDoesNotHavePublicKey_errorCode8() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest(new MockParam(RECIPIENT_PARAMETER, "123"));
    final Account mockSenderAccount = mock(Account.class);
    final Account mockRecipientAccount = mock(Account.class);

    when(parameterServiceMock.getAccount(eq(req))).thenReturn(mockSenderAccount);
    when(accountServiceMock.getAccount(eq(123L))).thenReturn(mockRecipientAccount);

    assertEquals(8, JSONTestHelper.errorCode(t.processRequest(req)));
  }
}
