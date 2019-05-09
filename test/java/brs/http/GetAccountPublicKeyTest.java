package brs.http;

import brs.Account;
import brs.BurstException;
import brs.common.QuickMocker;
import brs.common.TestConstants;
import brs.services.ParameterService;
import brs.util.JSON;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.ResultFields.PUBLIC_KEY_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetAccountPublicKeyTest {

  private GetAccountPublicKey t;

  private ParameterService mockParameterService;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);

    t = new GetAccountPublicKey(mockParameterService);
  }

  @Test
  public void processRequest() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getPublicKey()).thenReturn(TestConstants.TEST_PUBLIC_KEY_BYTES);

    when(mockParameterService.getAccount(eq(req))).thenReturn(mockAccount);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    assertEquals(TestConstants.TEST_PUBLIC_KEY, JSON.getAsString(result.get(PUBLIC_KEY_RESPONSE)));
  }

  @Test
  public void processRequest_withoutPublicKey() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getPublicKey()).thenReturn(null);

    when(mockParameterService.getAccount(eq(req))).thenReturn(mockAccount);

    assertEquals(JSON.emptyJSON, t.processRequest(req));
  }

}
