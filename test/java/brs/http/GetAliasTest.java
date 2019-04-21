package brs.http;

import brs.Alias;
import brs.Alias.Offer;
import brs.common.QuickMocker;
import brs.services.AliasService;
import brs.services.ParameterService;
import brs.util.JSON;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetAliasTest {

  private GetAlias t;

  private ParameterService mockParameterService;
  private AliasService mockAliasService;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);
    mockAliasService = mock(AliasService.class);

    t = new GetAlias(mockParameterService, mockAliasService);
  }

  @Test
  public void processRequest() throws ParameterException {
    final Alias mockAlias = mock(Alias.class);
    when(mockAlias.getAliasName()).thenReturn("mockAliasName");

    final Offer mockOffer = mock(Offer.class);
    when(mockOffer.getPriceNQT()).thenReturn(123L);
    when(mockOffer.getBuyerId()).thenReturn(345L);

    final HttpServletRequest req = QuickMocker.httpServletRequest();

    when(mockParameterService.getAlias(eq(req))).thenReturn(mockAlias);
    when(mockAliasService.getOffer(eq(mockAlias))).thenReturn(mockOffer);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);
    assertEquals(mockAlias.getAliasName(), JSON.getAsString(result.get(ALIAS_NAME_RESPONSE)));
    assertEquals("" + mockOffer.getPriceNQT(), JSON.getAsString(result.get(PRICE_NQT_RESPONSE)));
    assertEquals("" + mockOffer.getBuyerId(), JSON.getAsString(result.get(BUYER_RESPONSE)));
  }

}
