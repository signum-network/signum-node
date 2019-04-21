package brs.assetexchange;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import brs.AssetTransfer;
import brs.assetexchange.AssetTransferServiceImpl;
import brs.db.BurstIterator;
import brs.db.sql.EntitySqlTable;
import brs.db.store.AssetTransferStore;
import org.junit.Before;
import org.junit.Test;

public class AssetTransferServiceImplTest {

  private AssetTransferServiceImpl t;

  private AssetTransferStore mockAssetTransferStore;
  private EntitySqlTable<AssetTransfer> mockAssetTransferTable;

  @Before
  public void setUp() {
    mockAssetTransferStore = mock(AssetTransferStore.class);
    mockAssetTransferTable = mock(EntitySqlTable.class);

    when(mockAssetTransferStore.getAssetTransferTable()).thenReturn(mockAssetTransferTable);

    t = new AssetTransferServiceImpl(mockAssetTransferStore);
  }

  @Test
  public void getAssetTransfers() {
    final long assetId = 123L;
    final int from = 1;
    final int to = 4;

    final BurstIterator<AssetTransfer> mockAssetTransferIterator = mock(BurstIterator.class);

    when(mockAssetTransferStore.getAssetTransfers(eq(assetId), eq(from), eq(to))).thenReturn(mockAssetTransferIterator);

    assertEquals(mockAssetTransferIterator, t.getAssetTransfers(assetId, from, to));
  }

  @Test
  public void getAccountAssetTransfers() {
    final long accountId = 12L;
    final long assetId = 123L;
    final int from = 1;
    final int to = 4;

    final BurstIterator<AssetTransfer> mockAccountAssetTransferIterator = mock(BurstIterator.class);

    when(mockAssetTransferStore.getAccountAssetTransfers(eq(accountId), eq(assetId), eq(from), eq(to))).thenReturn(mockAccountAssetTransferIterator);

    assertEquals(mockAccountAssetTransferIterator, t.getAccountAssetTransfers(accountId, assetId, from, to));
  }

  @Test
  public void getTransferCount() {
    when(mockAssetTransferStore.getTransferCount(eq(123L))).thenReturn(5);

    assertEquals(5, t.getTransferCount(123L));
  }

  @Test
  public void getAssetTransferCount() {
    when(mockAssetTransferTable.getCount()).thenReturn(5);

    assertEquals(5, t.getAssetTransferCount());
  }
}

