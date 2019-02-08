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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
  JsonElement processRequest(HttpServletRequest req) {

    JsonObject response = new JsonObject();

    response.addProperty("application", Burst.APPLICATION);
    response.addProperty("version", Burst.VERSION.toString());
    response.addProperty(TIME_RESPONSE, timeService.getEpochTime());
    response.addProperty("lastBlock", blockchain.getLastBlock().getStringId());
    response.addProperty("cumulativeDifficulty", blockchain.getLastBlock().getCumulativeDifficulty().toString());


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
    response.addProperty("totalEffectiveBalanceNXT", totalEffectiveBalance / Constants.ONE_BURST);


    if (!"false".equalsIgnoreCase(req.getParameter("includeCounts"))) {
      response.addProperty("numberOfBlocks", blockchain.getHeight() + 1);
      response.addProperty("numberOfTransactions", blockchain.getTransactionCount());
      response.addProperty("numberOfAccounts", accountService.getCount());
      response.addProperty("numberOfAssets", assetExchange.getAssetsCount());
      int askCount = assetExchange.getAskCount();
      int bidCount = assetExchange.getBidCount();
      response.addProperty("numberOfOrders", askCount + bidCount);
      response.addProperty("numberOfAskOrders", askCount);
      response.addProperty("numberOfBidOrders", bidCount);
      response.addProperty("numberOfTrades", assetExchange.getTradesCount());
      response.addProperty("numberOfTransfers", assetExchange.getAssetTransferCount());
      response.addProperty("numberOfAliases", aliasService.getAliasCount());
      //response.addProperty("numberOfPolls", Poll.getCount());
      //response.addProperty("numberOfVotes", Vote.getCount());
    }
    response.addProperty("numberOfPeers", Peers.getAllPeers().size());
    response.addProperty("numberOfUnlockedAccounts", generator.getAllGenerators().size());
    Peer lastBlockchainFeeder = Burst.getBlockchainProcessor().getLastBlockchainFeeder();
    response.addProperty("lastBlockchainFeeder", lastBlockchainFeeder == null ? null : lastBlockchainFeeder.getAnnouncedAddress());
    response.addProperty("lastBlockchainFeederHeight", Burst.getBlockchainProcessor().getLastBlockchainFeederHeight());
    response.addProperty("isScanning", Burst.getBlockchainProcessor().isScanning());
    response.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
    response.addProperty("maxMemory", Runtime.getRuntime().maxMemory());
    response.addProperty("totalMemory", Runtime.getRuntime().totalMemory());
    response.addProperty("freeMemory", Runtime.getRuntime().freeMemory());

    return response;
  }

}
