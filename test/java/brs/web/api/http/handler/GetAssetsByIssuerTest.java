package brs.web.api.http.handler;

import brs.Account;
import brs.Asset;
import brs.Blockchain;
import brs.Signum;
import brs.SignumException;
import brs.assetexchange.AssetExchange;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.common.QuickMocker.MockParam;
import brs.services.AccountService;
import brs.services.ParameterService;
import brs.util.CollectionWithIndex;
import brs.util.JSON;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;

import static brs.web.api.http.common.Parameters.FIRST_INDEX_PARAMETER;
import static brs.web.api.http.common.Parameters.LAST_INDEX_PARAMETER;
import static brs.web.api.http.common.ResultFields.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;

public class GetAssetsByIssuerTest extends AbstractUnitTest {

  private MockedStatic<Signum> signumMock;

  private GetAssetsByIssuer t;

  private ParameterService mockParameterService;
  private AssetExchange mockAssetExchange;
  private AccountService mockAccountService;

  @Before
  public void setUp() {
    signumMock = mockStatic(Signum.class);
    mockParameterService = mock(ParameterService.class);
    mockAssetExchange = mock(AssetExchange.class);
    mockAccountService = mock(AccountService.class);
    Blockchain mockBlockchain = mock(Blockchain.class);
    when(Signum.getBlockchain()).thenReturn(mockBlockchain);
    when(mockBlockchain.getHeight()).thenReturn(Integer.MAX_VALUE);

    t = new GetAssetsByIssuer(mockParameterService, mockAssetExchange, mockAccountService);
  }

  @Test
  public void processRequest() throws SignumException {
    final int firstIndex = 1;
    final int lastIndex = 2;

    final HttpServletRequest req = QuickMocker.httpServletRequest(
        new MockParam(FIRST_INDEX_PARAMETER, firstIndex),
        new MockParam(LAST_INDEX_PARAMETER, lastIndex)
    );


    final Account mockAccount = mock(Account.class);
    when(mockAccount.getId()).thenReturn(1l);
    when(mockParameterService.getAccount(eq(req))).thenReturn(mockAccount);

    final long mockAssetId = 1;

    final Asset mockAsset = mock(Asset.class);
    when(mockAsset.getId()).thenReturn(1L);
    when(mockAsset.getId()).thenReturn(mockAssetId);
    when(mockAsset.getName()).thenReturn("name");
    when(mockAsset.getDescription()).thenReturn("description");
    when(mockAsset.getDecimals()).thenReturn((byte) 1);
    when(mockAsset.getQuantityQnt()).thenReturn(2L);

    final Collection<Asset> mockAssetIterator = mockCollection(mockAsset);

    when(mockAssetExchange.getAssetsIssuedBy(eq(mockAccount.getId()), eq(firstIndex), eq(lastIndex))).thenReturn(new CollectionWithIndex<Asset>(mockAssetIterator, -1));
    when(mockAssetExchange.getAssetAccountsCount(eq(mockAsset), eq(0L), eq(true), eq(true))).thenReturn(1);
    when(mockAssetExchange.getTransferCount(eq(mockAssetId))).thenReturn(2);
    when(mockAssetExchange.getTradeCount(eq(mockAssetId))).thenReturn(3);

    final JsonObject result = (JsonObject) t.processRequest(req);
    assertNotNull(result);

    final JsonArray assetsForAccountsResult = (JsonArray) result.get(ASSETS_RESPONSE);
    assertNotNull(assetsForAccountsResult);
    assertEquals(1, assetsForAccountsResult.size());

    final JsonObject assetResult = (JsonObject) assetsForAccountsResult.get(0);
    assertNotNull(assetResult);

    assertEquals(mockAsset.getName(), JSON.getAsString(assetResult.get(NAME_RESPONSE)));
    assertEquals(mockAsset.getDescription(), JSON.getAsString(assetResult.get(DESCRIPTION_RESPONSE)));
    assertEquals(mockAsset.getDecimals(), JSON.getAsByte(assetResult.get(DECIMALS_RESPONSE)));
    assertEquals("" + mockAsset.getQuantityQnt(), JSON.getAsString(assetResult.get(QUANTITY_QNT_RESPONSE)));
    assertEquals("" + mockAsset.getId(), JSON.getAsString(assetResult.get(ASSET_RESPONSE)));
  }

  @After
  public void tearDownSignumMock() {
    signumMock.close();
  }
}
