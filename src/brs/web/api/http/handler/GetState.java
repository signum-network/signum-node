package brs.web.api.http.handler;

import brs.*;
import brs.assetexchange.AssetExchange;
import brs.peer.Peer;
import brs.peer.Peers;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.ATService;
import brs.services.AccountService;
import brs.services.AliasService;
import brs.services.EscrowService;
import brs.services.TimeService;

import brs.web.api.http.ApiServlet;
import brs.web.api.http.common.LegacyDocTag;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServletRequest;

import static brs.web.api.http.common.Parameters.INCLUDE_COUNTS_PARAMETER;
import static brs.web.api.http.common.JSONResponses.ERROR_NOT_ALLOWED;
import static brs.web.api.http.common.Parameters.API_KEY_PARAMETER;
import static brs.web.api.http.common.ResultFields.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class GetState extends ApiServlet.JsonRequestHandler {

  private final Blockchain blockchain;
  private final AssetExchange assetExchange;
  private final AccountService accountService;
  private final AliasService aliasService;
  private final TimeService timeService;
  private final ATService atService;
  private final Generator generator;
  private final PropertyService propertyService;
  private final List<String> apiAdminKeyList;

  public GetState(Blockchain blockchain, AssetExchange assetExchange, AccountService accountService, EscrowService escrowService,
                  AliasService aliasService, TimeService timeService, ATService atService, Generator generator, PropertyService propertyService) {
    super(new LegacyDocTag[] {LegacyDocTag.INFO}, INCLUDE_COUNTS_PARAMETER, API_KEY_PARAMETER);
    this.blockchain = blockchain;
    this.assetExchange = assetExchange;
    this.accountService = accountService;
    this.aliasService = aliasService;
    this.timeService = timeService;
    this.atService = atService;
    this.generator = generator;
    this.propertyService = propertyService;

    apiAdminKeyList = propertyService.getStringList(Props.API_ADMIN_KEY_LIST);
  }

  @Override
  protected
  JsonElement processRequest(HttpServletRequest req) {

    JsonObject response = new JsonObject();

    response.addProperty("application", propertyService.getString(Props.APPLICATION));
    response.addProperty("version", propertyService.getString(Props.VERSION));
    response.addProperty(TIME_RESPONSE, timeService.getEpochTime());

    Block lastBlock = blockchain.getLastBlock();
    response.addProperty("lastBlock", lastBlock.getStringId());
    response.addProperty(CUMULATIVE_DIFFICULTY_RESPONSE, lastBlock.getCumulativeDifficulty().toString());

    long totalMined = blockchain.getTotalMined();
    Account.Balance burnAccountBalance = Signum.getStores().getAccountStore().getAccountBalanceTable().get(
            Signum.getStores().getAccountStore().getAccountKeyFactory().newKey(0L));
    // A chain on which nothing was ever burnt has no balance row for the burn account (id 0).
    long totalBurnt = burnAccountBalance == null ? 0L : burnAccountBalance.getBalanceNqt();
    response.addProperty("totalMinedNQT", totalMined);
    response.addProperty("totalBurntNQT", totalBurnt);
    response.addProperty("circulatingSupplyNQT", totalMined - totalBurnt);

    if ("true".equalsIgnoreCase(req.getParameter(INCLUDE_COUNTS_PARAMETER))) {
      String apiKey = req.getParameter(API_KEY_PARAMETER);
      if (!apiAdminKeyList.contains(apiKey)) {
        return ERROR_NOT_ALLOWED;
      }

      CompletableFuture<Long> effectiveBalanceFuture = CompletableFuture.supplyAsync(accountService::getAllAccountsBalance);
      CompletableFuture<Long> committedFuture = CompletableFuture.supplyAsync(
              () -> blockchain.getCommittedAmount(0L, blockchain.getHeight(), blockchain.getHeight(), null));

      CompletableFuture.allOf(effectiveBalanceFuture, committedFuture).join();

      long totalEffectiveBalance = effectiveBalanceFuture.join();
      response.addProperty("totalEffectiveBalance", totalEffectiveBalance / propertyService.getInt(Props.ONE_COIN_NQT));
      response.addProperty("totalEffectiveBalanceNQT", totalEffectiveBalance);
      response.addProperty("totalCommittedNQT", committedFuture.join());
    }

    CompletableFuture<Integer> accountCountFuture      = CompletableFuture.supplyAsync(accountService::getCount);
    CompletableFuture<Integer> atCountFuture          = CompletableFuture.supplyAsync(atService::getATCount);
    CompletableFuture<Integer> assetsCountFuture      = CompletableFuture.supplyAsync(assetExchange::getAssetsCount);
    CompletableFuture<Integer> askCountFuture         = CompletableFuture.supplyAsync(assetExchange::getAskCount);
    CompletableFuture<Integer> bidCountFuture         = CompletableFuture.supplyAsync(assetExchange::getBidCount);
    CompletableFuture<Integer> tradesCountFuture      = CompletableFuture.supplyAsync(assetExchange::getTradesCount);
    CompletableFuture<Integer> transferCountFuture    = CompletableFuture.supplyAsync(assetExchange::getAssetTransferCount);
    CompletableFuture<Integer> aliasCountFuture       = CompletableFuture.supplyAsync(aliasService::getAliasCount);
    CompletableFuture<Integer> txCountFuture          = CompletableFuture.supplyAsync(blockchain::getTransactionCount);
    CompletableFuture<Integer> subscriptionFuture     = CompletableFuture.supplyAsync(() ->
            blockchain.countTransactions(TransactionType.TYPE_ADVANCED_PAYMENT.getType(),
                    TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE,
                    TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE));
    CompletableFuture<Integer> subscriptionPayFuture  = CompletableFuture.supplyAsync(() ->
            blockchain.countTransactions(TransactionType.TYPE_ADVANCED_PAYMENT.getType(),
                    TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT,
                    TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT));

    CompletableFuture.allOf(accountCountFuture, atCountFuture, assetsCountFuture, askCountFuture,
            bidCountFuture, tradesCountFuture, transferCountFuture, aliasCountFuture,
            txCountFuture, subscriptionFuture, subscriptionPayFuture).join();

    int askCount = askCountFuture.join();
    int bidCount = bidCountFuture.join();

    response.addProperty("numberOfAccounts", accountCountFuture.join());
    response.addProperty("numberOfBlocks", blockchain.getHeight() + 1);
    response.addProperty("numberOfTransactions", txCountFuture.join());
    response.addProperty("numberOfATs", atCountFuture.join());
    response.addProperty("numberOfAssets", assetsCountFuture.join());
    response.addProperty("numberOfOrders", askCount + bidCount);
    response.addProperty("numberOfAskOrders", askCount);
    response.addProperty("numberOfBidOrders", bidCount);
    response.addProperty("numberOfTrades", tradesCountFuture.join());
    response.addProperty("numberOfTransfers", transferCountFuture.join());
    response.addProperty("numberOfAliases", aliasCountFuture.join());
    response.addProperty("numberOfSubscriptions", subscriptionFuture.join());
    response.addProperty("numberOfSubscriptionPayments", subscriptionPayFuture.join());

    response.addProperty("numberOfPeers", Peers.getAllPeers().size());
    response.addProperty("numberOfUnlockedAccounts", generator.getAllGenerators().size());

    BlockchainProcessor blockchainProcessor = Signum.getBlockchainProcessor();
    Peer lastBlockchainFeeder = blockchainProcessor.getLastBlockchainFeeder();
    response.addProperty("lastBlockchainFeeder", lastBlockchainFeeder == null ? null : lastBlockchainFeeder.getAnnouncedAddress());
    response.addProperty("lastBlockchainFeederHeight", blockchainProcessor.getLastBlockchainFeederHeight());
    response.addProperty("isScanning", blockchainProcessor.isScanning());

    Runtime runtime = Runtime.getRuntime();
    response.addProperty("availableProcessors", runtime.availableProcessors());
    response.addProperty("maxMemory", runtime.maxMemory());
    response.addProperty("totalMemory", runtime.totalMemory());
    response.addProperty("freeMemory", runtime.freeMemory());

    response.addProperty("indirectIncomingServiceEnabled", propertyService.getBoolean(Props.INDIRECT_INCOMING_SERVICE_ENABLE));
    response.addProperty("databaseTrimmingEnabled", propertyService.getBoolean(Props.DB_TRIM_DERIVED_TABLES));
    response.addProperty("webUIEnabled", propertyService.getBoolean(Props.WEB_UI_ENABLED));

    return response;
  }
}
