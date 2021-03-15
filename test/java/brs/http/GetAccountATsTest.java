package brs.http;

import brs.Account;
import brs.BurstException;
import brs.at.AT;
import brs.at.AtConstants;
import brs.at.AtMachineState;
import brs.common.QuickMocker;
import brs.services.ATService;
import brs.services.AccountService;
import brs.services.ParameterService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;

import static brs.http.common.ResultFields.ATS_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAccountATsTest {

  private GetAccountATs t;

  private ParameterService mockParameterService;
  private ATService mockATService;
  private AccountService mockAccountService;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);
    mockATService = mock(ATService.class);
    mockAccountService = mock(AccountService.class);

    t = new GetAccountATs(mockParameterService, mockATService);
  }

  @Test
  public void processRequest() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final long mockAccountId = 123L;
    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(mockAccountId);

    final long mockATId = 1L;
    byte[] mockATIDBytes = new byte[ AtConstants.AT_ID_SIZE ];
    byte[] creatorBytes = new byte[]{(byte) 'c', (byte) 'r', (byte) 'e', (byte) 'a', (byte) 't', (byte) 'o', (byte) 'r', (byte) 'r'};
    final AtMachineState.MachineState mockMachineState = mock(AtMachineState.MachineState.class);
    final AT mockAT = mock(AT.class);
    when(mockAT.getCreator()).thenReturn(creatorBytes);
    when(mockAT.getId()).thenReturn(mockATIDBytes);
    when(mockAT.getMachineState()).thenReturn(mockMachineState);

    when(mockParameterService.getAccount(eq(req))).thenReturn(mockAccount);

    when(mockAccountService.getAccount(anyLong())).thenReturn(mockAccount);

    when(mockATService.getATsIssuedBy(eq(mockAccountId))).thenReturn(Arrays.asList(mockATId));
    when(mockATService.getAT(eq(mockATId))).thenReturn(mockAT);
    when(mockATService.getAT(eq(mockATId), ArgumentMatchers.anyInt())).thenReturn(mockAT);

    JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray atsResultList = (JsonArray) result.get(ATS_RESPONSE);
    assertNotNull(atsResultList);
    assertEquals(1, atsResultList.size());

    final JsonObject atsResult = (JsonObject) atsResultList.get(0);
    assertNotNull(atsResult);
  }

}
