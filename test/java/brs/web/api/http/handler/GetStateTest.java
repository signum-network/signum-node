package brs.web.api.http.handler;

import brs.Account;
import brs.Block;
import brs.Blockchain;
import brs.BlockchainProcessor;
import brs.Generator;
import brs.Signum;
import brs.assetexchange.AssetExchange;
import brs.common.AbstractUnitTest;
import brs.common.QuickMocker;
import brs.db.SignumKey;
import brs.db.VersionedBatchEntityTable;
import brs.db.store.AccountStore;
import brs.db.store.Stores;
import brs.peer.Peer;
import brs.peer.Peers;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.ATService;
import brs.services.AccountService;
import brs.services.AliasService;
import brs.services.EscrowService;
import brs.services.TimeService;

import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Uses Mockito's own static mocking rather than PowerMock, which is incompatible with the
 * Mockito version this project builds against.
 */
public class GetStateTest extends AbstractUnitTest {

  private GetState t;

  private Blockchain blockchain;
  private Stores stores;
  private VersionedBatchEntityTable<Account.Balance> balanceTable;

  @Before
  @SuppressWarnings("unchecked")
  public void setUp() {
    blockchain = mock(Blockchain.class);
    final AssetExchange assetExchange = mock(AssetExchange.class);
    final AccountService accountService = mock(AccountService.class);
    final EscrowService escrowService = mock(EscrowService.class);
    final AliasService aliasService = mock(AliasService.class);
    final TimeService timeService = mock(TimeService.class);
    final ATService atService = mock(ATService.class);
    final Generator generator = mock(Generator.class);
    final PropertyService propertyService = mock(PropertyService.class);

    when(propertyService.getStringList(Props.API_ADMIN_KEY_LIST)).thenReturn(Collections.emptyList());

    final Block lastBlock = mock(Block.class);
    when(lastBlock.getStringId()).thenReturn("4711");
    when(lastBlock.getCumulativeDifficulty()).thenReturn(BigInteger.ZERO);
    when(blockchain.getLastBlock()).thenReturn(lastBlock);

    when(generator.getAllGenerators()).thenReturn(Collections.emptyList());

    final AccountStore accountStore = mock(AccountStore.class);
    final SignumKey.LongKeyFactory<Account> keyFactory = mock(SignumKey.LongKeyFactory.class);
    balanceTable = mock(VersionedBatchEntityTable.class);
    stores = mock(Stores.class);

    when(stores.getAccountStore()).thenReturn(accountStore);
    when(accountStore.getAccountBalanceTable()).thenReturn(balanceTable);
    when(accountStore.getAccountKeyFactory()).thenReturn(keyFactory);

    t = new GetState(blockchain, assetExchange, accountService, escrowService, aliasService,
        timeService, atService, generator, propertyService);
  }

  /**
   * On a chain where nothing has ever been burnt - a freshly created mock or test chain -
   * the burn account (id 0) has no balance row at all. Reporting zero burnt is the only
   * sensible answer; the request must not fail.
   */
  @Test
  public void processRequestWithoutBurnAccountReportsZeroBurnt() {
    when(balanceTable.get(any())).thenReturn(null);
    when(blockchain.getTotalMined()).thenReturn(5_000L);

    final JsonObject result = processRequest();

    assertNotNull(result);
    assertEquals(5_000L, result.get("totalMinedNQT").getAsLong());
    assertEquals(0L, result.get("totalBurntNQT").getAsLong());
    assertEquals(5_000L, result.get("circulatingSupplyNQT").getAsLong());
  }

  @Test
  public void processRequestWithBurnAccountReportsItsBalance() {
    final Account.Balance burnAccount = mock(Account.Balance.class);
    when(burnAccount.getBalanceNqt()).thenReturn(1_000L);
    when(balanceTable.get(any())).thenReturn(burnAccount);
    when(blockchain.getTotalMined()).thenReturn(5_000L);

    final JsonObject result = processRequest();

    assertNotNull(result);
    assertEquals(1_000L, result.get("totalBurntNQT").getAsLong());
    assertEquals(4_000L, result.get("circulatingSupplyNQT").getAsLong());
  }

  private JsonObject processRequest() {
    final Collection<Peer> noPeers = Collections.emptyList();
    try (MockedStatic<Signum> signum = mockStatic(Signum.class);
         MockedStatic<Peers> peers = mockStatic(Peers.class)) {
      signum.when(Signum::getStores).thenReturn(stores);
      signum.when(Signum::getBlockchainProcessor).thenReturn(mock(BlockchainProcessor.class));
      peers.when(Peers::getAllPeers).thenReturn(noPeers);

      return (JsonObject) t.processRequest(QuickMocker.httpServletRequest());
    }
  }
}
