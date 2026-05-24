package brs.web.server;

import brs.*;
import brs.assetexchange.AssetExchange;
import brs.deeplink.DeeplinkQRCodeGenerator;
import brs.feesuggestions.FeeSuggestionCalculator;
import brs.props.PropertyService;
import brs.services.*;
import brs.util.ThreadPool;
import brs.web.api.http.common.APITransactionManager;
import signum.net.NetworkParameters;
import brs.services.NetworkAnalysisService;

public class WebServerContext {
    private final TransactionProcessor transactionProcessor;
    private final Blockchain blockchain;
    private final BlockchainProcessor blockchainProcessor;
    private final ParameterService parameterService;
    private final AccountService accountService;
    private final AliasService aliasService;
    private final AssetExchange assetExchange;
    private final EscrowService escrowService;
    private final DGSGoodsStoreService digitalGoodsStoreService;
    private final SubscriptionService subscriptionService;
    private final ATService atService;
    private final TimeService timeService;
    private final EconomicClustering economicClustering;
    private final PropertyService propertyService;
    private final ThreadPool threadPool;
    private final TransactionService transactionService;
    private final BlockService blockService;
    private final Generator generator;
    private final APITransactionManager apiTransactionManager;
    private final FeeSuggestionCalculator feeSuggestionCalculator;
    private final DeeplinkQRCodeGenerator deepLinkQRCodeGenerator;
    private final IndirectIncomingService indirectIncomingService;
    private final NetworkAnalysisService networkAnalysisService;
    private final NetworkParameters params;

    public WebServerContext(TransactionProcessor transactionProcessor, Blockchain blockchain, BlockchainProcessor blockchainProcessor, ParameterService parameterService, AccountService accountService, AliasService aliasService, AssetExchange assetExchange, EscrowService escrowService, DGSGoodsStoreService digitalGoodsStoreService, SubscriptionService subscriptionService, ATService atService, TimeService timeService, EconomicClustering economicClustering, PropertyService propertyService, ThreadPool threadPool, TransactionService transactionService, BlockService blockService, Generator generator, APITransactionManager apiTransactionManager, FeeSuggestionCalculator feeSuggestionCalculator, DeeplinkQRCodeGenerator deepLinkQRCodeGenerator, IndirectIncomingService indirectIncomingService, NetworkAnalysisService networkAnalysisService, NetworkParameters params) {
        this.transactionProcessor = transactionProcessor;
        this.blockchain = blockchain;
        this.blockchainProcessor = blockchainProcessor;
        this.parameterService = parameterService;
        this.accountService = accountService;
        this.aliasService = aliasService;
        this.assetExchange = assetExchange;
        this.escrowService = escrowService;
        this.digitalGoodsStoreService = digitalGoodsStoreService;
        this.subscriptionService = subscriptionService;
        this.atService = atService;
        this.timeService = timeService;
        this.economicClustering = economicClustering;
        this.propertyService = propertyService;
        this.threadPool = threadPool;
        this.transactionService = transactionService;
        this.blockService = blockService;
        this.generator = generator;
        this.apiTransactionManager = apiTransactionManager;
        this.feeSuggestionCalculator = feeSuggestionCalculator;
        this.deepLinkQRCodeGenerator = deepLinkQRCodeGenerator;
        this.indirectIncomingService = indirectIncomingService;
        this.networkAnalysisService = networkAnalysisService;
        this.params = params;
    }

    public TransactionProcessor getTransactionProcessor() {
        return transactionProcessor;
    }

    public Blockchain getBlockchain() {
        return blockchain;
    }

    public BlockchainProcessor getBlockchainProcessor() {
        return blockchainProcessor;
    }

    public ParameterService getParameterService() {
        return parameterService;
    }

    public AccountService getAccountService() {
        return accountService;
    }

    public AliasService getAliasService() {
        return aliasService;
    }

    public AssetExchange getAssetExchange() {
        return assetExchange;
    }

    public EscrowService getEscrowService() {
        return escrowService;
    }

    public DGSGoodsStoreService getDigitalGoodsStoreService() {
        return digitalGoodsStoreService;
    }

    public SubscriptionService getSubscriptionService() {
        return subscriptionService;
    }

    public ATService getATService() {
        return atService;
    }

    public TimeService getTimeService() {
        return timeService;
    }

    public EconomicClustering getEconomicClustering() {
        return economicClustering;
    }

    public PropertyService getPropertyService() {
        return propertyService;
    }

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    public TransactionService getTransactionService() {
        return transactionService;
    }

    public BlockService getBlockService() {
        return blockService;
    }

    public Generator getGenerator() {
        return generator;
    }

    public APITransactionManager getApiTransactionManager() {
        return apiTransactionManager;
    }

    public FeeSuggestionCalculator getFeeSuggestionCalculator() {
        return feeSuggestionCalculator;
    }

    public DeeplinkQRCodeGenerator getDeepLinkQRCodeGenerator() {
        return deepLinkQRCodeGenerator;
    }

    public IndirectIncomingService getIndirectIncomingService() {
        return indirectIncomingService;
    }

    public NetworkAnalysisService getNetworkAnalysisService() {
        return networkAnalysisService;
    }

    public NetworkParameters getNetworkParameters() {
        return params;
    }
}
