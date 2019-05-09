package brs.assetexchange;

import brs.Account.AccountAsset;
import brs.Asset;
import brs.AssetTransfer;
import brs.Attachment.ColoredCoinsAssetIssuance;
import brs.Trade;
import brs.Transaction;
import brs.common.AbstractUnitTest;
import brs.db.BurstKey;
import brs.db.BurstKey.LongKeyFactory;
import brs.db.sql.EntitySqlTable;
import brs.db.store.AssetStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AssetServiceImplTest extends AbstractUnitTest {

  private AssetServiceImpl t;

  private AssetAccountServiceImpl assetAccountServiceMock;
  private AssetTransferServiceImpl assetTransferServicMock;
  private TradeServiceImpl tradeServiceMock;
  private AssetStore assetStoreMock;
  private EntitySqlTable assetTableMock;
  private LongKeyFactory assetDbKeyFactoryMock;

  @Before
  public void setUp() {
    assetAccountServiceMock = mock(AssetAccountServiceImpl.class);
    assetTransferServicMock = mock(AssetTransferServiceImpl.class);
    tradeServiceMock = mock(TradeServiceImpl.class);

    assetStoreMock = mock(AssetStore.class);
    assetTableMock = mock(EntitySqlTable.class);
    assetDbKeyFactoryMock = mock(LongKeyFactory.class);

    when(assetStoreMock.getAssetTable()).thenReturn(assetTableMock);
    when(assetStoreMock.getAssetDbKeyFactory()).thenReturn(assetDbKeyFactoryMock);

    t = new AssetServiceImpl(assetAccountServiceMock, tradeServiceMock, assetStoreMock, assetTransferServicMock);
  }

  @Test
  public void getAsset() {
    final long assetId = 123l;
    final Asset mockAsset = mock(Asset.class);
    final BurstKey assetKeyMock = mock(BurstKey.class);

    when(assetDbKeyFactoryMock.newKey(eq(assetId))).thenReturn(assetKeyMock);
    when(assetTableMock.get(eq(assetKeyMock))).thenReturn(mockAsset);

    assertEquals(mockAsset, t.getAsset(assetId));
  }

  @Test
  public void getAccounts() {
    final long assetId = 123l;
    final int from = 1;
    final int to = 5;

    final Collection<AccountAsset> mockAccountAssetIterator = mock(Collection.class);

    when(assetAccountServiceMock.getAssetAccounts(eq(assetId), eq(from), eq(to))).thenReturn(mockAccountAssetIterator);

    assertEquals(mockAccountAssetIterator, t.getAccounts(assetId, from, to));
  }

  @Test
  public void getAccounts_forHeight() {
    final long assetId = 123l;
    final int from = 1;
    final int to = 5;
    final int height = 3;

    final Collection<AccountAsset> mockAccountAssetIterator = mock(Collection.class);

    when(assetAccountServiceMock.getAssetAccounts(eq(assetId), eq(height), eq(from), eq(to))).thenReturn(mockAccountAssetIterator);

    assertEquals(mockAccountAssetIterator, t.getAccounts(assetId, height, from, to));
  }

  @Test
  public void getAccounts_forHeight_negativeHeightGivesForZeroHeight() {
    final long assetId = 123l;
    final int from = 1;
    final int to = 5;
    final int height = -3;

    final Collection<AccountAsset> mockAccountAssetIterator = mock(Collection.class);

    when(assetAccountServiceMock.getAssetAccounts(eq(assetId), eq(from), eq(to))).thenReturn(mockAccountAssetIterator);

    assertEquals(mockAccountAssetIterator, t.getAccounts(assetId, height, from, to));
  }

  @Test
  public void getTrades() {
    final long assetId = 123l;
    final int from = 2;
    final int to = 4;

    final Collection<Trade> mockTradeIterator = mock(Collection.class);

    when(tradeServiceMock.getAssetTrades(eq(assetId), eq(from), eq(to))).thenReturn(mockTradeIterator);

    assertEquals(mockTradeIterator, t.getTrades(assetId, from, to));
  }

  @Test
  public void getAssetTransfers() {
    final long assetId = 123l;
    final int from = 2;
    final int to = 4;

    final Collection<AssetTransfer> mockTransferIterator = mock(Collection.class);

    when(assetTransferServicMock.getAssetTransfers(eq(assetId), eq(from), eq(to))).thenReturn(mockTransferIterator);

    assertEquals(mockTransferIterator, t.getAssetTransfers(assetId, from, to));
  }

  @Test
  public void getAllAssetsTest() {
    final int from = 2;
    final int to = 4;

    final Collection<Trade> mockTradeIterator = mock(Collection.class);

    when(assetTableMock.getAll(eq(from), eq(to))).thenReturn(mockTradeIterator);

    assertEquals(mockTradeIterator, t.getAllAssets(from, to));
  }

  @Test
  public void getAssetsIssuesBy() {
    long accountId = 123L;
    int from = 1;
    int to = 2;

    Collection<Asset> mockAssetIterator = mockCollection();
    when(assetStoreMock.getAssetsIssuedBy(eq(accountId), eq(from), eq(to))).thenReturn(mockAssetIterator);

    assertEquals(mockAssetIterator, t.getAssetsIssuedBy(accountId, from, to));
  }

  @Test
  public void getCount() {
    when(assetTableMock.getCount()).thenReturn(5);

    assertEquals(5, t.getAssetsCount());
  }

  @Test
  public void addAsset() {
    final BurstKey assetKey = mock(BurstKey.class);

    long transactionId = 123L;

    when(assetDbKeyFactoryMock.newKey(eq(transactionId))).thenReturn(assetKey);

    final ArgumentCaptor<Asset> savedAssetCaptor = ArgumentCaptor.forClass(Asset.class);

    final Transaction transaction = mock(Transaction.class);
    when(transaction.getId()).thenReturn(transactionId);

    ColoredCoinsAssetIssuance attachment = mock(ColoredCoinsAssetIssuance.class);
    t.addAsset(transaction, attachment);

    verify(assetTableMock).insert(savedAssetCaptor.capture());

    final Asset savedAsset = savedAssetCaptor.getValue();
    assertNotNull(savedAsset);

    assertEquals(assetKey, savedAsset.dbKey);
    assertEquals(transaction.getId(), savedAsset.getId());
    assertEquals(transaction.getSenderId(), savedAsset.getAccountId());
    assertEquals(attachment.getName(), savedAsset.getName());
    assertEquals(attachment.getDescription(), savedAsset.getDescription());
    assertEquals(attachment.getQuantityQNT(), savedAsset.getQuantityQNT());
    assertEquals(attachment.getDecimals(), savedAsset.getDecimals());
  }
}
