package brs.http;

import brs.Account;
import brs.BurstException;
import brs.common.QuickMocker;
import brs.services.ParameterService;
import brs.util.JSON;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetBalanceTest {

  private GetBalance t;

  private ParameterService parameterServiceMock;

  @Before
  public void setUp() {
    parameterServiceMock = mock(ParameterService.class);
    this.t = new GetBalance(parameterServiceMock);
  }

  @Test
  public void processRequest() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();
    Account mockAccount = mock(Account.class);

    when(mockAccount.getBalanceNQT()).thenReturn(1L);
    when(mockAccount.getUnconfirmedBalanceNQT()).thenReturn(2L);
    when(mockAccount.getForgedBalanceNQT()).thenReturn(3L);

    when(parameterServiceMock.getAccount(eq(req))).thenReturn(mockAccount);

    JsonObject result = (JsonObject) t.processRequest(req);

    assertEquals("1", JSON.getAsString(result.get(BALANCE_NQT_RESPONSE)));
    assertEquals("2", JSON.getAsString(result.get(UNCONFIRMED_BALANCE_NQT_RESPONSE)));
    assertEquals("1", JSON.getAsString(result.get(EFFECTIVE_BALANCE_NQT_RESPONSE)));
    assertEquals("3", JSON.getAsString(result.get(FORGED_BALANCE_NQT_RESPONSE)));
    assertEquals("1", JSON.getAsString(result.get(GUARANTEED_BALANCE_NQT_RESPONSE)));
  }

  @Test
  public void processRequest_noAccountFound() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    when(parameterServiceMock.getAccount(eq(req))).thenReturn(null);

    JsonObject result = (JsonObject) t.processRequest(req);

    assertEquals("0", JSON.getAsString(result.get(BALANCE_NQT_RESPONSE)));
    assertEquals("0", JSON.getAsString(result.get(UNCONFIRMED_BALANCE_NQT_RESPONSE)));
    assertEquals("0", JSON.getAsString(result.get(EFFECTIVE_BALANCE_NQT_RESPONSE)));
    assertEquals("0", JSON.getAsString(result.get(FORGED_BALANCE_NQT_RESPONSE)));
    assertEquals("0", JSON.getAsString(result.get(GUARANTEED_BALANCE_NQT_RESPONSE)));
  }
}
