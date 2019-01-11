package brs.http;

import brs.*;
import brs.assetexchange.AssetExchange;
import brs.db.BurstIterator;
import brs.peer.Peer;
import brs.peer.Peers;
import brs.services.AccountService;
import brs.services.AliasService;
import brs.services.EscrowService;
import brs.services.TimeService;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static brs.http.common.Parameters.INCLUDE_COUNTS_PARAMETER;
import static brs.http.common.ResultFields.TIME_RESPONSE;

final class GetState extends APIServlet.APIRequestHandler {

  private final Blockchain blockchain;
  private final AssetExchange assetExchange;
  private final AccountService accountService;
  private final EscrowService escrowService;
  private final AliasService aliasService;
  private final TimeService timeService;
  private final Generator generator;

  GetState(Blockchain blockchain, AssetExchange assetExchange, AccountService accountService, EscrowService escrowService,
      AliasService aliasService, TimeService timeService, Generator generator) {
    super(new APITag[] {APITag.INFO}, INCLUDE_COUNTS_PARAMETER);
    this.blockchain = blockchain;
    this.assetExchange = assetExchange;
    this.accountService = accountService;
    this.escrowService = escrowService;
    this.aliasService = aliasService;
    this.timeService = timeService;
    this.generator = generator;
  }

  @Override
  JSONStreamAware processRequest(HttpServletRequest req) {

    JSONObject response = new JSONObject();

    response.put("application", Burst.APPLICATION);
    response.put("version", Burst.VERSION.toString());
    response.put(TIME_RESPONSE, timeService.getEpochTime());
    response.put("lastBlock", blockchain.getLastBlock().getStringId());
    response.put("cumulativeDifficulty", blockchain.getLastBlock().getCumulativeDifficulty().toString());


    long totalEffectiveBalance = 0;
    try (BurstIterator<Account> accounts = accountService.getAllAccounts(0, -1)) {
      while(accounts.hasNext()) {
        long effectiveBalanceBURST = accounts.next().getBalanceNQT();
        if (effectiveBalanceBURST > 0) {
          totalEffectiveBalance += effectiveBalanceBURST;
        }
      }
    }
    try(BurstIterator<Escrow> escrows = escrowService.getAllEscrowTransactions()) {
      while(escrows.hasNext()) {
        totalEffectiveBalance += escrows.next().getAmountNQT();
      }
    }
    response.put("totalEffectiveBalanceNXT", totalEffectiveBalance / Constants.ONE_BURST);


    if (!"false".equalsIgnoreCase(req.getParameter("includeCounts"))) {
      response.put("numberOfBlocks", blockchain.getHeight() + 1);
      response.put("numberOfTransactions", blockchain.getTransactionCount());
      response.put("numberOfAccounts", accountService.getCount());
      response.put("numberOfAssets", assetExchange.getAssetsCount());
      int askCount = assetExchange.getAskCount();
      int bidCount = assetExchange.getBidCount();
      response.put("numberOfOrders", askCount + bidCount);
      response.put("numberOfAskOrders", askCount);
      response.put("numberOfBidOrders", bidCount);
      response.put("numberOfTrades", assetExchange.getTradesCount());
      response.put("numberOfTransfers", assetExchange.getAssetTransferCount());
      response.put("numberOfAliases", aliasService.getAliasCount());
      //response.put("numberOfPolls", Poll.getCount());
      //response.put("numberOfVotes", Vote.getCount());
    }
    response.put("numberOfPeers", Peers.getAllPeers().size());
    response.put("numberOfUnlockedAccounts", generator.getAllGenerators().size());
    Peer lastBlockchainFeeder = Burst.getBlockchainProcessor().getLastBlockchainFeeder();
    response.put("lastBlockchainFeeder", lastBlockchainFeeder == null ? null : lastBlockchainFeeder.getAnnouncedAddress());
    response.put("lastBlockchainFeederHeight", Burst.getBlockchainProcessor().getLastBlockchainFeederHeight());
    response.put("isScanning", Burst.getBlockchainProcessor().isScanning());
    response.put("availableProcessors", Runtime.getRuntime().availableProcessors());
    response.put("maxMemory", Runtime.getRuntime().maxMemory());
    response.put("totalMemory", Runtime.getRuntime().totalMemory());
    response.put("freeMemory", Runtime.getRuntime().freeMemory());

    return response;
  }

}
