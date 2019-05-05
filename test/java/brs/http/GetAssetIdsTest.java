package brs.http;

import brs.Asset;
import brs.assetexchange.AssetExchange;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.http.common.Parameters.FIRST_INDEX_PARAMETER;
import static brs.http.common.Parameters.LAST_INDEX_PARAMETER;
import static brs.http.common.ResultFields.ASSET_IDS_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAssetIdsTest extends AbstractUnitTest {

  private GetAssetIds t;

  private AssetExchange mockAssetExchange;

  @Before
  public void setUp() {
    mockAssetExchange = mock(AssetExchange.class);

    t = new GetAssetIds(mockAssetExchange);
  }

  @Test
  public void processRequest() {
    int firstIndex = 1;
    int lastIndex = 2;

    final Asset mockAsset = mock(Asset.class);
    when(mockAsset.getId()).thenReturn(5L);

    final Collection<Asset> mockAssetIterator = mockCollection(mockAsset);

    when(mockAssetExchange.getAllAssets(eq(firstIndex), eq(lastIndex)))
        .thenReturn(mockAssetIterator);

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, lastIndex)
    );

    final JsonObject result = (JsonObject) t.processRequest(req);

    assertNotNull(result);

    final JsonArray resultAssetIds = (JsonArray) result.get(ASSET_IDS_RESPONSE);
    assertNotNull(resultAssetIds);
    assertEquals(1, resultAssetIds.size());

    final String resultAssetId = JSON.getAsString(resultAssetIds.get(0));
    assertEquals("5", resultAssetId);
  }

}
