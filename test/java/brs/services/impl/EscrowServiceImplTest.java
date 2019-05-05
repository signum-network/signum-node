package brs.services.impl;

import brs.Blockchain;
import brs.Escrow;
import brs.db.BurstKey;
import brs.db.BurstKey.LongKeyFactory;
import brs.db.VersionedEntityTable;
import brs.db.store.EscrowStore;
import brs.services.AccountService;
import brs.services.AliasService;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EscrowServiceImplTest {

  private EscrowServiceImpl t;

  private EscrowStore mockEscrowStore;
  private VersionedEntityTable<Escrow> mockEscrowTable;
  private LongKeyFactory<Escrow> mockEscrowDbKeyFactory;
  private Blockchain blockchainMock;
  private AliasService aliasServiceMock;
  private AccountService accountServiceMock;

  @Before
  public void setUp() {
    mockEscrowStore = mock(EscrowStore.class);
    mockEscrowTable = mock(VersionedEntityTable.class);
    mockEscrowDbKeyFactory = mock(LongKeyFactory.class);

    blockchainMock = mock(Blockchain.class);
    aliasServiceMock = mock(AliasService.class);
    accountServiceMock = mock(AccountService.class);

    when(mockEscrowStore.getEscrowTable()).thenReturn(mockEscrowTable);
    when(mockEscrowStore.getEscrowDbKeyFactory()).thenReturn(mockEscrowDbKeyFactory);

    t = new EscrowServiceImpl(mockEscrowStore, blockchainMock, aliasServiceMock, accountServiceMock);
  }


  @Test
  public void getAllEscrowTransactions() {
    final Collection<Escrow> mockEscrowIterator = mock(Collection.class);

    when(mockEscrowTable.getAll(eq(0), eq(-1))).thenReturn(mockEscrowIterator);

    assertEquals(mockEscrowIterator, t.getAllEscrowTransactions());
  }

  @Test
  public void getEscrowTransaction() {
    final long escrowId = 123L;

    final BurstKey mockEscrowKey = mock(BurstKey.class);
    final Escrow mockEscrow = mock(Escrow.class);

    when(mockEscrowDbKeyFactory.newKey(eq(escrowId))).thenReturn(mockEscrowKey);
    when(mockEscrowTable.get(eq(mockEscrowKey))).thenReturn(mockEscrow);

    assertEquals(mockEscrow, t.getEscrowTransaction(escrowId));
  }
}
