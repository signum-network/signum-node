package brs.assetexchange;

import brs.Account.AccountAsset;
import brs.db.store.AccountStore;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AssetAccountServiceImplTest {

  private AssetAccountServiceImpl t;

  private AccountStore mockAccountStore;

  @Before
  public void setUp() {
    mockAccountStore = mock(AccountStore.class);

    t = new AssetAccountServiceImpl(mockAccountStore);
  }

  @Test
  public void getAssetAccounts() {
    final long assetId = 4L;
    final int from = 1;
    final int to = 5;

    final Collection<AccountAsset> mockAccountIterator = mock(Collection.class);

    when(mockAccountStore.getAssetAccounts(eq(assetId), eq(from), eq(to))).thenReturn(mockAccountIterator);

    assertEquals(mockAccountIterator, t.getAssetAccounts(assetId, from, to));
  }

  @Test
  public void getAssetAccounts_withHeight() {
    final long assetId = 4L;
    final int from = 1;
    final int to = 5;
    final int height = 3;

    final Collection<AccountAsset> mockAccountIterator = mock(Collection.class);

    when(mockAccountStore.getAssetAccounts(eq(assetId), eq(height), eq(from), eq(to))).thenReturn(mockAccountIterator);

    assertEquals(mockAccountIterator, t.getAssetAccounts(assetId, height, from, to));
  }

  @Test
  public void getAssetAccounts_withHeight_negativeHeightGivesForZeroHeight() {
    final long assetId = 4L;
    final int from = 1;
    final int to = 5;
    final int height = -2;

    final Collection<AccountAsset> mockAccountIterator = mock(Collection.class);

    when(mockAccountStore.getAssetAccounts(eq(assetId), eq(from), eq(to))).thenReturn(mockAccountIterator);

    assertEquals(mockAccountIterator, t.getAssetAccounts(assetId, height, from, to));
  }

  @Test
  public void getAssetAccountsCount() {
    when(mockAccountStore.getAssetAccountsCount(eq(123L))).thenReturn(5);

    assertEquals(5L, t.getAssetAccountsCount(123));
  }
}
