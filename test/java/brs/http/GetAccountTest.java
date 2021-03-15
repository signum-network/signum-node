package brs.http;

import brs.Account;
import brs.Blockchain;
import brs.BurstException;
import brs.Generator;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.services.ParameterService;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

;

public class GetAccountTest extends AbstractUnitTest {

  private GetAccount t;

  private ParameterService parameterServiceMock;
  private Blockchain blockchainMock;
  private Generator generatorMock;

  @Before
  public void setUp() {
    parameterServiceMock = mock(ParameterService.class);
    blockchainMock = mock(Blockchain.class);
    generatorMock = mock(Generator.class);

    t = new GetAccount(parameterServiceMock, blockchainMock, generatorMock);
  }

  @Test
  public void processRequest() throws BurstException {
    final long mockAccountId = 123L;
    final String mockAccountName = "accountName";
    final String mockAccountDescription = "accountDescription";

    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(mockAccountId);
    when(mockAccount.getPublicKey()).thenReturn(new byte[]{(byte) 1});
    when(mockAccount.getName()).thenReturn(mockAccountName);
    when(mockAccount.getDescription()).thenReturn(mockAccountDescription);

    when(parameterServiceMock.getAccount(eq(req))).thenReturn(mockAccount);
  }
}
