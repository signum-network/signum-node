package brs.services.impl;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import brs.AT;
import brs.db.store.ATStore;
import brs.services.ATService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

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

    assertEquals(mockAT, t.getAT(atId));
  }

}
