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

    response.addProperty("application", Signum.getPropertyService().getString(Props.APPLICATION));
    response.addProperty("version", Signum.getPropertyService().getString(Props.VERSION));
    response.addProperty(TIME_RESPONSE, timeService.getEpochTime());
    response.addProperty("lastBlock", blockchain.getLastBlock().getStringId());
    response.addProperty(CUMULATIVE_DIFFICULTY_RESPONSE, blockchain.getLastBlock().getCumulativeDifficulty().toString());
    long totalMined = blockchain.getTotalMined();
    long totalBurnt = Signum.getStores().getAccountStore().getAccountBalanceTable().get(
            Signum.getStores().getAccountStore().getAccountKeyFactory().newKey(0L)).getBalanceNqt();
    response.addProperty("totalMinedNQT", totalMined);
    response.addProperty("totalBurntNQT", totalBurnt);
    response.addProperty("circulatingSupplyNQT", totalMined - totalBurnt);

    if ("true".equalsIgnoreCase(req.getParameter(INCLUDE_COUNTS_PARAMETER))) {
      String apiKey = req.getParameter(API_KEY_PARAMETER);
      if(!apiAdminKeyList.contains(apiKey)) {
        return ERROR_NOT_ALLOWED;
      }

      long totalEffectiveBalance = accountService.getAllAccountsBalance();
      response.addProperty("totalEffectiveBalance", totalEffectiveBalance / propertyService.getInt(Props.ONE_COIN_NQT));
      response.addProperty("totalEffectiveBalanceNQT", totalEffectiveBalance);

      long totalCommitted = blockchain.getCommittedAmount(0L, blockchain.getHeight(), blockchain.getHeight(), null);
      response.addProperty("totalCommittedNQT", totalCommitted);

      response.addProperty("numberOfAccounts", accountService.getCount());
    }

    // TODO: maybe we should parallelize the calls.

    response.addProperty("numberOfBlocks", blockchain.getHeight() + 1);
    response.addProperty("numberOfTransactions", blockchain.getTransactionCount());
    response.addProperty("numberOfATs", atService.getAllATIds(null).size());
    response.addProperty("numberOfAssets", assetExchange.getAssetsCount());
    int askCount = assetExchange.getAskCount();
    int bidCount = assetExchange.getBidCount();
    response.addProperty("numberOfOrders", askCount + bidCount);
    response.addProperty("numberOfAskOrders", askCount);
    response.addProperty("numberOfBidOrders", bidCount);
    response.addProperty("numberOfTrades", assetExchange.getTradesCount());
    response.addProperty("numberOfTransfers", assetExchange.getAssetTransferCount());
    response.addProperty("numberOfAliases", aliasService.getAliasCount());

    response.addProperty("numberOfSubscriptions", blockchain.countTransactions(TransactionType.TYPE_ADVANCED_PAYMENT.getType(),
            TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE, TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_SUBSCRIBE));
    response.addProperty("numberOfSubscriptionPayments", blockchain.countTransactions(TransactionType.TYPE_ADVANCED_PAYMENT.getType(),
            TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT, TransactionType.SUBTYPE_ADVANCED_PAYMENT_SUBSCRIPTION_PAYMENT));

    response.addProperty("numberOfPeers", Peers.getAllPeers().size());
    response.addProperty("numberOfUnlockedAccounts", generator.getAllGenerators().size());
    Peer lastBlockchainFeeder = Signum.getBlockchainProcessor().getLastBlockchainFeeder();
    response.addProperty("lastBlockchainFeeder", lastBlockchainFeeder == null ? null : lastBlockchainFeeder.getAnnouncedAddress());
    response.addProperty("lastBlockchainFeederHeight", Signum.getBlockchainProcessor().getLastBlockchainFeederHeight());
    response.addProperty("isScanning", Signum.getBlockchainProcessor().isScanning());
    response.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
    response.addProperty("maxMemory", Runtime.getRuntime().maxMemory());
    response.addProperty("totalMemory", Runtime.getRuntime().totalMemory());
    response.addProperty("freeMemory", Runtime.getRuntime().freeMemory());
    response.addProperty("indirectIncomingServiceEnabled", propertyService.getBoolean(Props.INDIRECT_INCOMING_SERVICE_ENABLE));
    response.addProperty("databaseTrimmingEnabled", propertyService.getBoolean(Props.DB_TRIM_DERIVED_TABLES));
    response.addProperty("webUIEnabled", propertyService.getBoolean(Props.WEB_UI_ENABLED));

    return response;
  }
}
