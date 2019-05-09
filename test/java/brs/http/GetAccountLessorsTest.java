package brs.http;

import brs.Account;
import brs.Blockchain;
import brs.BurstException;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.services.ParameterService;
import brs.util.JSON;
import com.google.gson.JsonObject;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.ResultFields.ACCOUNT_RESPONSE;
import static brs.http.common.ResultFields.LESSORS_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAccountLessorsTest extends AbstractUnitTest {

  private ParameterService parameterServiceMock;
  private Blockchain blockchainMock;

  private GetAccountLessors t;

  @Before
  public void setUp() {
    parameterServiceMock = mock(ParameterService.class);
    blockchainMock = mock(Blockchain.class);

    t = new GetAccountLessors(parameterServiceMock, blockchainMock);
  }

  @Test
  public void processRequest() throws BurstException {
    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(123L);

    final HttpServletRequest req = QuickMocker.httpServletRequest();

    when(parameterServiceMock.getAccount(eq(req))).thenReturn(mockAccount);
    when(parameterServiceMock.getHeight(eq(req))).thenReturn(0);

    final JsonObject result = JSON.getAsJsonObject(t.processRequest(req));

    assertNotNull(result);
    assertEquals("" + mockAccount.getId(), JSON.getAsString(result.get(ACCOUNT_RESPONSE)));
    TestCase.assertEquals(0, JSON.getAsJsonArray(result.get(LESSORS_RESPONSE)).size());
  }

}
