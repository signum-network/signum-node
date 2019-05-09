package brs.http;

import brs.Account;
import brs.Alias;
import brs.Alias.Offer;
import brs.BurstException;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.services.AliasService;
import brs.services.ParameterService;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAliasesTest extends AbstractUnitTest {

  private GetAliases t;

  private ParameterService mockParameterService;
  private AliasService mockAliasService;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);
    mockAliasService = mock(AliasService.class);

    t = new GetAliases(mockParameterService, mockAliasService);
  }

  @Test
  public void processRequest() throws BurstException {
    final long accountId = 123L;
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(accountId);

    final Alias mockAlias = mock(Alias.class);
    when(mockAlias.getId()).thenReturn(567L);

    final Offer mockOffer = mock(Offer.class);
    when(mockOffer.getPriceNQT()).thenReturn(234L);

    final Collection<Alias> mockAliasIterator = mockCollection(mockAlias);

    when(mockParameterService.getAccount(eq(req))).thenReturn(mockAccount);

    when(mockAliasService.getAliasesByOwner(eq(accountId), eq(0), eq(-1))).thenReturn(mockAliasIterator);
    when(mockAliasService.getOffer(eq(mockAlias))).thenReturn(mockOffer);

    final JsonObject resultOverview = (JsonObject) t.processRequest(req);
    assertNotNull(resultOverview);

    final JsonArray resultList = (JsonArray) resultOverview.get(ALIASES_RESPONSE);
    assertNotNull(resultList);
    assertEquals(1, resultList.size());

    final JsonObject result = (JsonObject) resultList.get(0);
    assertNotNull(result);
    assertEquals("" +mockAlias.getId(), JSON.getAsString(result.get(ALIAS_RESPONSE)));
    assertEquals("" + mockOffer.getPriceNQT(), JSON.getAsString(result.get(PRICE_NQT_RESPONSE)));
  }

}
