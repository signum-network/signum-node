package brs.assetexchange;

import brs.AssetTransfer;
import brs.db.sql.EntitySqlTable;
import brs.db.store.AssetTransferStore;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    final Collection<AssetTransfer> mockAssetTransferIterator = mock(Collection.class);

    when(mockAssetTransferStore.getAssetTransfers(eq(assetId), eq(from), eq(to))).thenReturn(mockAssetTransferIterator);

    assertEquals(mockAssetTransferIterator, t.getAssetTransfers(assetId, from, to));
  }

  @Test
  public void getAccountAssetTransfers() {
    final long accountId = 12L;
    final long assetId = 123L;
    final int from = 1;
    final int to = 4;

    final Collection<AssetTransfer> mockAccountAssetTransferIterator = mock(Collection.class);

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

