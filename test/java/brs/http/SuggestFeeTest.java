package brs.http;

import brs.BurstException;
import brs.common.QuickMocker;
import brs.feesuggestions.FeeSuggestion;
import brs.feesuggestions.FeeSuggestionCalculator;
import brs.util.JSON;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

import static brs.Constants.FEE_QUANT;
import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SuggestFeeTest {

  private SuggestFee t;

  private FeeSuggestionCalculator feeSuggestionCalculator;

  @Before
  public void setUp() {
    feeSuggestionCalculator = mock(FeeSuggestionCalculator.class);

    t = new SuggestFee(feeSuggestionCalculator);
  }

  @Test
  public void processRequest() throws BurstException {
    final HttpServletRequest req = QuickMocker.httpServletRequest();

    final long cheap = 1 * FEE_QUANT;
    final long standard = 5 * FEE_QUANT;
    final long priority = 10 * FEE_QUANT;
    final FeeSuggestion feeSuggestion = new FeeSuggestion(cheap, standard, priority);

    when(feeSuggestionCalculator.giveFeeSuggestion()).thenReturn(feeSuggestion);

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertEquals(cheap, JSON.getAsLong(result.get(CHEAP_FEE_RESPONSE)));
    assertEquals(standard, JSON.getAsLong(result.get(STANDARD_FEE_RESPONSE)));
    assertEquals(priority, JSON.getAsLong(result.get(PRIORITY_FEE_RESPONSE)));
  }
}