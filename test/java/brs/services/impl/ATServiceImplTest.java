package brs.services.impl;

import brs.at.AT;
import brs.db.store.ATStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ATServiceImplTest {

  private ATServiceImpl t;

  private ATStore mockATStore;

  @Before
  public void setUp() {
    mockATStore = mock(ATStore.class);

    t = new ATServiceImpl(mockATStore);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void getAllATIds() {
    final Collection<Long> mockATCollection = mock(Collection.class);

    when(mockATStore.getAllATIds()).thenReturn(mockATCollection);

    assertEquals(mockATCollection, t.getAllATIds());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void getATsIssuedBy() {
    final long accountId = 1L;

    final List<Long> mockATsIssuedByAccount = mock(List.class);

    when(mockATStore.getATsIssuedBy(eq(accountId))).thenReturn(mockATsIssuedByAccount);

    assertEquals(mockATsIssuedByAccount, t.getATsIssuedBy(accountId));
  }

  @Test
  public void getAT() {
    final long atId = 123L;

    final AT mockAT = mock(AT.class);

    when(mockATStore.getAT(eq(atId))).thenReturn(mockAT);
    when(mockATStore.getAT(eq(atId), ArgumentMatchers.anyInt())).thenReturn(mockAT);

    assertEquals(mockAT, t.getAT(atId));
  }

}
