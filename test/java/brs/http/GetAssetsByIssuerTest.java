package brs.http;

import brs.Account;
import brs.Asset;
import brs.assetexchange.AssetExchange;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.services.ParameterService;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;

import static brs.http.common.Parameters.FIRST_INDEX_PARAMETER;
import static brs.http.common.Parameters.LAST_INDEX_PARAMETER;
import static brs.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

;

public class GetAssetsByIssuerTest extends AbstractUnitTest {

  private GetAssetsByIssuer t;

  private ParameterService mockParameterService;
  private AssetExchange mockAssetExchange;

  @Before
  public void setUp() {
    mockParameterService = mock(ParameterService.class);
    mockAssetExchange = mock(AssetExchange.class);

    t = new GetAssetsByIssuer(mockParameterService, mockAssetExchange);
  }

  @Test
  public void processRequest() throws ParameterException {
    final int firstIndex = 1;
    final int lastIndex = 2;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, lastIndex)
    );


    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(1l);
    when(mockParameterService.getAccounts(eq(req))).thenReturn(Arrays.asList(mockAccount));

    final long mockAssetId = 1;

    final Asset mockAsset = mock(Asset.class);
    when(mockAsset.getId()).thenReturn(1L);
    when(mockAsset.getId()).thenReturn(mockAssetId);
    when(mockAsset.getName()).thenReturn("name");
    when(mockAsset.getDescription()).thenReturn("description");
    when(mockAsset.getDecimals()).thenReturn((byte) 1);
    when(mockAsset.getQuantityQNT()).thenReturn(2L);

    final Collection<Asset> mockAssetIterator = mockCollection(mockAsset);

    when(mockAssetExchange.getAssetsIssuedBy(eq(mockAccount.getId()), eq(firstIndex), eq(lastIndex))).thenReturn(mockAssetIterator);
    when(mockAssetExchange.getAssetAccountsCount(eq(mockAssetId))).thenReturn(1);
    when(mockAssetExchange.getTransferCount(eq(mockAssetId))).thenReturn(2);
    when(mockAssetExchange.getTradeCount(eq(mockAssetId))).thenReturn(3);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray assetsForAccountsResult = (JsonArray) result.get(ASSETS_RESPONSE);
    assertNotNull(assetsForAccountsResult);
    assertEquals(1, assetsForAccountsResult.size());

    final JsonArray assetsForAccount1Result = (JsonArray) assetsForAccountsResult.get(0);
    assertNotNull(assetsForAccount1Result);
    assertEquals(1, assetsForAccount1Result.size());

    final JsonObject assetResult = (JsonObject) assetsForAccount1Result.get(0);
    assertNotNull(assetResult);

    assertEquals(mockAsset.getName(), JSON.getAsString(assetResult.get(NAME_RESPONSE)));
    assertEquals(mockAsset.getDescription(), JSON.getAsString(assetResult.get(DESCRIPTION_RESPONSE)));
    assertEquals(mockAsset.getDecimals(), JSON.getAsByte(assetResult.get(DECIMALS_RESPONSE)));
    assertEquals("" + mockAsset.getQuantityQNT(), JSON.getAsString(assetResult.get(QUANTITY_QNT_RESPONSE)));
    assertEquals("" + mockAsset.getId(), JSON.getAsString(assetResult.get(ASSET_RESPONSE)));
    assertEquals(1, JSON.getAsInt(assetResult.get(NUMBER_OF_ACCOUNTS_RESPONSE)));
    assertEquals(2, JSON.getAsInt(assetResult.get(NUMBER_OF_TRANSFERS_RESPONSE)));
    assertEquals(3, JSON.getAsInt(assetResult.get(NUMBER_OF_TRADES_RESPONSE)));
  }

}
