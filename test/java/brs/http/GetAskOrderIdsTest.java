package brs.http;

import brs.Asset;
import brs.BurstException;
import brs.Order.Ask;
import brs.assetexchange.AssetExchange;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.services.ParameterService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.http.common.Parameters.*;
import static brs.http.common.ResultFields.ASK_ORDER_IDS_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAskOrderIdsTest extends AbstractUnitTest {

  private ParameterService parameterServiceMock;
  private AssetExchange assetExchangeMock;

  private GetAskOrderIds t;

  @Before
  public void setUp() {
    parameterServiceMock = mock(ParameterService.class);
    assetExchangeMock = mock(AssetExchange.class);

    t = new GetAskOrderIds(parameterServiceMock, assetExchangeMock);
  }

  @Test
  public void processRequest() throws BurstException {
    final long assetIndex = 5;
    final int firstIndex = 1;
    final int lastIndex = 3;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
      new MockParam(ASSET_PARAMETER, assetIndex),
      new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
      new MockParam(LAST_INDEX_PARAMETER, lastIndex)
    );

    final Asset asset = mock(Asset.class);
    when(asset.getId()).thenReturn(assetIndex);

    when(parameterServiceMock.getAsset(eq(req))).thenReturn(asset);

    final Ask askOrder1 = mock(Ask.class);
    when(askOrder1.getId()).thenReturn(5L);
    final Ask askOrder2 = mock(Ask.class);
    when(askOrder1.getId()).thenReturn(6L);

    final Collection<Ask> askIterator = this.mockCollection(askOrder1, askOrder2);

    when(assetExchangeMock.getSortedAskOrders(eq(assetIndex), eq(firstIndex), eq(lastIndex))).thenReturn(askIterator);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray ids = (JsonArray) result.get(ASK_ORDER_IDS_RESPONSE);
    assertNotNull(ids);

    assertEquals(2, ids.size());
  }
}
