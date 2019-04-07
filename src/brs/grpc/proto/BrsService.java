package brs.grpc.proto;

import brs.Blockchain;
import brs.BlockchainProcessor;
import brs.Generator;
import brs.TransactionProcessor;
import brs.assetexchange.AssetExchange;
import brs.feesuggestions.FeeSuggestionCalculator;
import brs.fluxcapacitor.FluxCapacitor;
import brs.grpc.GrpcApiHandler;
import brs.grpc.handlers.*;
import brs.services.*;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BrsService extends BrsApiServiceGrpc.BrsApiServiceImplBase {

    private final Map<Class<? extends GrpcApiHandler<? extends Message,? extends Message>>, GrpcApiHandler<? extends Message,? extends Message>> handlers;

    public BrsService(BlockchainProcessor blockchainProcessor, Blockchain blockchain, BlockService blockService, AccountService accountService, Generator generator, TransactionProcessor transactionProcessor, TimeService timeService, FeeSuggestionCalculator feeSuggestionCalculator, ATService atService, AliasService aliasService, IndirectIncomingService indirectIncomingService, FluxCapacitor fluxCapacitor, EscrowService escrowService, AssetExchange assetExchange, SubscriptionService subscriptionService, DGSGoodsStoreService digitalGoodsStoreService) {
        Map<Class<? extends GrpcApiHandler<? extends Message,? extends Message>>, GrpcApiHandler<? extends Message,? extends Message>> handlerMap = new HashMap<>();
        handlerMap.put(BroadcastTransactionHandler.class, new BroadcastTransactionHandler(transactionProcessor));
        handlerMap.put(CompleteBasicTransactionHandler.class, new CompleteBasicTransactionHandler(timeService, transactionProcessor, blockchain));
        handlerMap.put(GetAccountATsHandler.class, new GetAccountATsHandler(atService, accountService));
        handlerMap.put(GetAccountBlocksHandler.class, new GetAccountBlocksHandler(blockchain, blockService, accountService));
        handlerMap.put(GetAccountCurrentOrdersHandler.class, new GetAccountCurrentOrdersHandler(assetExchange));
        handlerMap.put(GetAccountEscrowTransactionsHandler.class, new GetAccountEscrowTransactionsHandler(escrowService));
        handlerMap.put(GetAccountHandler.class, new GetAccountHandler(accountService));
        handlerMap.put(GetAccountsHandler.class, new GetAccountsHandler(accountService));
        handlerMap.put(GetAccountSubscriptionsHandler.class, new GetAccountSubscriptionsHandler(subscriptionService));
        handlerMap.put(GetAccountTransactionsHandler.class, new GetAccountTransactionsHandler(blockchain, accountService));
        handlerMap.put(GetAliasesHandler.class, new GetAliasesHandler(aliasService));
        handlerMap.put(GetAliasHandler.class, new GetAliasHandler(aliasService));
        handlerMap.put(GetAssetBalancesHandler.class, new GetAssetBalancesHandler(assetExchange));
        handlerMap.put(GetAssetHandler.class, new GetAssetHandler(assetExchange));
        handlerMap.put(GetAssetsByIssuerHandler.class, new GetAssetsByIssuerHandler(assetExchange));
        handlerMap.put(GetAssetsHandler.class, new GetAssetsHandler(assetExchange));
        handlerMap.put(GetAssetTradesHandler.class, new GetAssetTradesHandler(assetExchange));
        handlerMap.put(GetAssetTransfersHandler.class, new GetAssetTransfersHandler(assetExchange, accountService));
        handlerMap.put(GetATHandler.class, new GetATHandler(atService, accountService));
        handlerMap.put(GetATIdsHandler.class, new GetATIdsHandler(atService));
        handlerMap.put(GetBlockHandler.class, new GetBlockHandler(blockchain, blockService));
        handlerMap.put(GetBlocksHandler.class, new GetBlocksHandler(blockchain, blockService));
        handlerMap.put(GetConstantsHandler.class, new GetConstantsHandler(fluxCapacitor));
        handlerMap.put(GetCountsHandler.class, new GetCountsHandler(accountService, escrowService, blockchain, assetExchange, aliasService, generator));
        handlerMap.put(GetCurrentTimeHandler.class, new GetCurrentTimeHandler(timeService));
        handlerMap.put(GetDgsGoodHandler.class, new GetDgsGoodHandler(digitalGoodsStoreService));
        handlerMap.put(GetDgsGoodsHandler.class, new GetDgsGoodsHandler(digitalGoodsStoreService));
        handlerMap.put(GetDgsPendingPurchasesHandler.class, new GetDgsPendingPurchasesHandler(digitalGoodsStoreService));
        handlerMap.put(GetDgsPurchaseHandler.class, new GetDgsPurchaseHandler(digitalGoodsStoreService));
        handlerMap.put(GetDgsPurchasesHandler.class, new GetDgsPurchasesHandler(digitalGoodsStoreService));
        handlerMap.put(GetEscrowTransactionHandler.class, new GetEscrowTransactionHandler(escrowService));
        handlerMap.put(GetMiningInfoHandler.class, new GetMiningInfoHandler(blockchainProcessor, generator));
        handlerMap.put(GetOrderHandler.class, new GetOrderHandler(assetExchange));
        handlerMap.put(GetOrdersHandler.class, new GetOrdersHandler(assetExchange));
        handlerMap.put(GetPeersHandler.class, new GetPeersHandler());
        handlerMap.put(GetStateHandler.class, new GetStateHandler(timeService, blockchain, generator, blockchainProcessor));
        handlerMap.put(GetSubscriptionHandler.class, new GetSubscriptionHandler(subscriptionService));
        handlerMap.put(GetSubscriptionsToAccountHandler.class, new GetSubscriptionsToAccountHandler(subscriptionService));
        handlerMap.put(GetTransactionBytesHandler.class, new GetTransactionBytesHandler(blockchain));
        handlerMap.put(GetTransactionHandler.class, new GetTransactionHandler(blockchain, transactionProcessor));
        handlerMap.put(GetUnconfirmedTransactionsHandler.class, new GetUnconfirmedTransactionsHandler(indirectIncomingService, transactionProcessor));
        handlerMap.put(ParseTransactionHandler.class, new ParseTransactionHandler());
        handlerMap.put(SubmitNonceHandler.class, new SubmitNonceHandler(blockchain, accountService, generator));
        handlerMap.put(SuggestFeeHandler.class, new SuggestFeeHandler(feeSuggestionCalculator));
        this.handlers = Collections.unmodifiableMap(handlerMap);
    }

    private <Handler extends GrpcApiHandler<Request, Response>, Request extends Message, Response extends Message> void handleRequest(Class<Handler> handlerClass, Request request, StreamObserver<Response> response) {
        GrpcApiHandler<? extends Message, ? extends Message> handler = handlers.get(handlerClass);
        if (handlerClass != null && handlerClass.isInstance(handler)) {
            Handler handlerInstance = handlerClass.cast(handler);
            handlerInstance.handleRequest(request, response);
        } else {
            response.onError(ProtoBuilder.buildError(new HandlerNotFoundException("Handler not registered: " + handlerClass)));
        }
    }

    @Override
    public void getMiningInfo(Empty request, StreamObserver<BrsApi.MiningInfo> responseObserver) {
        handleRequest(GetMiningInfoHandler.class, request, responseObserver);
    }

    @Override
    public void submitNonce(BrsApi.SubmitNonceRequest request, StreamObserver<BrsApi.SubmitNonceResponse> responseObserver) {
        handleRequest(SubmitNonceHandler.class, request, responseObserver);
    }

    @Override
    public void getAccount(BrsApi.GetAccountRequest request, StreamObserver<BrsApi.Account> responseObserver) {
        handleRequest(GetAccountHandler.class, request, responseObserver);
    }

    @Override
    public void getAccounts(BrsApi.GetAccountsRequest request, StreamObserver<BrsApi.Accounts> responseObserver) {
        handleRequest(GetAccountsHandler.class, request, responseObserver);
    }

    @Override
    public void getBlock(BrsApi.GetBlockRequest request, StreamObserver<BrsApi.Block> responseObserver) {
        handleRequest(GetBlockHandler.class, request, responseObserver);
    }

    @Override
    public void getTransaction(BrsApi.GetTransactionRequest request, StreamObserver<BrsApi.Transaction> responseObserver) {
        handleRequest(GetTransactionHandler.class, request, responseObserver);
    }

    @Override
    public void getTransactionBytes(BrsApi.BasicTransaction request, StreamObserver<BrsApi.TransactionBytes> responseObserver) {
        handleRequest(GetTransactionBytesHandler.class, request, responseObserver);
    }

    @Override
    public void completeBasicTransaction(BrsApi.BasicTransaction request, StreamObserver<BrsApi.BasicTransaction> responseObserver) {
        handleRequest(CompleteBasicTransactionHandler.class, request, responseObserver);
    }

    @Override
    public void getCurrentTime(Empty request, StreamObserver<BrsApi.Time> responseObserver) {
        handleRequest(GetCurrentTimeHandler.class, request, responseObserver);
    }

    @Override
    public void broadcastTransaction(BrsApi.TransactionBytes request, StreamObserver<BrsApi.TransactionBroadcastResult> responseObserver) {
        handleRequest(BroadcastTransactionHandler.class, request, responseObserver);
    }

    @Override
    public void getState(Empty request, StreamObserver<BrsApi.State> responseObserver) {
        handleRequest(GetStateHandler.class, request, responseObserver);
    }

    @Override
    public void getPeers(BrsApi.GetPeersRequest request, StreamObserver<BrsApi.Peers> responseObserver) {
        handleRequest(GetPeersHandler.class, request, responseObserver);
    }

    @Override
    public void getPeer(BrsApi.GetPeerRequest request, StreamObserver<BrsApi.Peer> responseObserver) {
        handleRequest(GetPeerHandler.class, request, responseObserver);
    }

    @Override
    public void suggestFee(Empty request, StreamObserver<BrsApi.FeeSuggestion> responseObserver) {
        handleRequest(SuggestFeeHandler.class, request, responseObserver);
    }

    @Override
    public void parseTransaction(BrsApi.TransactionBytes request, StreamObserver<BrsApi.BasicTransaction> responseObserver) {
        handleRequest(ParseTransactionHandler.class, request, responseObserver);
    }

    @Override
    public void getAccountATs(BrsApi.GetAccountRequest request, StreamObserver<BrsApi.AccountATs> responseObserver) {
        handleRequest(GetAccountATsHandler.class, request, responseObserver);
    }

    @Override
    public void getAT(BrsApi.GetByIdRequest request, StreamObserver<BrsApi.AT> responseObserver) {
        handleRequest(GetATHandler.class, request, responseObserver);
    }

    @Override
    public void getATIds(Empty request, StreamObserver<BrsApi.ATIds> responseObserver) {
        handleRequest(GetATIdsHandler.class, request, responseObserver);
    }

    @Override
    public void getAlias(BrsApi.GetAliasRequest request, StreamObserver<BrsApi.Alias> responseObserver) {
        handleRequest(GetAliasHandler.class, request, responseObserver);
    }

    @Override
    public void getAliases(BrsApi.GetAliasesRequest request, StreamObserver<BrsApi.Aliases> responseObserver) {
        handleRequest(GetAliasesHandler.class, request, responseObserver);
    }

    @Override
    public void getUnconfirmedTransactions(BrsApi.GetAccountRequest request, StreamObserver<BrsApi.UnconfirmedTransactions> responseObserver) {
        handleRequest(GetUnconfirmedTransactionsHandler.class, request, responseObserver);
    }

    @Override
    public void getAccountBlocks(BrsApi.GetAccountBlocksRequest request, StreamObserver<BrsApi.Blocks> responseObserver) {
        handleRequest(GetAccountBlocksHandler.class, request, responseObserver);
    }

    @Override
    public void getAccountCurrentOrders(BrsApi.GetAccountOrdersRequest request, StreamObserver<BrsApi.Orders> responseObserver) {
        handleRequest(GetAccountCurrentOrdersHandler.class, request, responseObserver);
    }

    @Override
    public void getAccountEscrowTransactions(BrsApi.GetAccountRequest request, StreamObserver<BrsApi.EscrowTransactions> responseObserver) {
        handleRequest(GetAccountEscrowTransactionsHandler.class, request, responseObserver);
    }

    @Override
    public void getAccountSubscriptions(BrsApi.GetAccountRequest request, StreamObserver<BrsApi.Subscriptions> responseObserver) {
        handleRequest(GetAccountSubscriptionsHandler.class, request, responseObserver);
    }

    @Override
    public void getAccountTransactions(BrsApi.GetAccountTransactionsRequest request, StreamObserver<BrsApi.Transactions> responseObserver) {
        handleRequest(GetAccountTransactionsHandler.class, request, responseObserver);
    }

    @Override
    public void getAsset(BrsApi.GetByIdRequest request, StreamObserver<BrsApi.Asset> responseObserver) {
        handleRequest(GetAssetHandler.class, request, responseObserver);
    }

    @Override
    public void getAssetBalances(BrsApi.GetAssetBalancesRequest request, StreamObserver<BrsApi.AssetBalances> responseObserver) {
        handleRequest(GetAssetBalancesHandler.class, request, responseObserver);
    }

    @Override
    public void getAssets(BrsApi.GetAssetsRequest request, StreamObserver<BrsApi.Assets> responseObserver) {
        handleRequest(GetAssetsHandler.class, request, responseObserver);
    }

    @Override
    public void getAssetsByIssuer(BrsApi.GetAccountRequest request, StreamObserver<BrsApi.Assets> responseObserver) {
        handleRequest(GetAssetsByIssuerHandler.class, request, responseObserver);
    }

    @Override
    public void getAssetTrades(BrsApi.GetAssetTransfersRequest request, StreamObserver<BrsApi.AssetTrades> responseObserver) {
        handleRequest(GetAssetTradesHandler.class, request, responseObserver);
    }

    @Override
    public void getAssetTransfers(BrsApi.GetAssetTransfersRequest request, StreamObserver<BrsApi.AssetTransfers> responseObserver) {
        handleRequest(GetAssetTransfersHandler.class, request, responseObserver);
    }

    @Override
    public void getBlocks(BrsApi.GetBlocksRequest request, StreamObserver<BrsApi.Blocks> responseObserver) {
        handleRequest(GetBlocksHandler.class, request, responseObserver);
    }

    @Override
    public void getConstants(Empty request, StreamObserver<BrsApi.Constants> responseObserver) {
        handleRequest(GetConstantsHandler.class, request, responseObserver);
    }

    @Override
    public void getCounts(Empty request, StreamObserver<BrsApi.Counts> responseObserver) {
        handleRequest(GetCountsHandler.class, request, responseObserver);
    }

    @Override
    public void getDgsGood(BrsApi.GetByIdRequest request, StreamObserver<BrsApi.DgsGood> responseObserver) {
        handleRequest(GetDgsGoodHandler.class, request, responseObserver);
    }

    @Override
    public void getDgsGoods(BrsApi.GetDgsGoodsRequest request, StreamObserver<BrsApi.DgsGoods> responseObserver) {
        handleRequest(GetDgsGoodsHandler.class, request, responseObserver);
    }

    @Override
    public void getDgsPendingPurchases(BrsApi.GetDgsPendingPurchasesRequest request, StreamObserver<BrsApi.DgsPurchases> responseObserver) {
        handleRequest(GetDgsPendingPurchasesHandler.class, request, responseObserver);
    }

    @Override
    public void getDgsPurchase(BrsApi.GetByIdRequest request, StreamObserver<BrsApi.DgsPurchase> responseObserver) {
        handleRequest(GetDgsPurchaseHandler.class, request, responseObserver);
    }

    @Override
    public void getDgsPurchases(BrsApi.GetDgsPurchasesRequest request, StreamObserver<BrsApi.DgsPurchases> responseObserver) {
        handleRequest(GetDgsPurchasesHandler.class, request, responseObserver);
    }

    @Override
    public void getEscrowTransaction(BrsApi.GetByIdRequest request, StreamObserver<BrsApi.EscrowTransaction> responseObserver) {
        handleRequest(GetEscrowTransactionHandler.class, request, responseObserver);
    }

    @Override
    public void getOrder(BrsApi.GetOrderRequest request, StreamObserver<BrsApi.Order> responseObserver) {
        handleRequest(GetOrderHandler.class, request, responseObserver);
    }

    @Override
    public void getOrders(BrsApi.GetOrdersRequest request, StreamObserver<BrsApi.Orders> responseObserver) {
        handleRequest(GetOrdersHandler.class, request, responseObserver);
    }

    @Override
    public void getSubscription(BrsApi.GetByIdRequest request, StreamObserver<BrsApi.Subscription> responseObserver) {
        handleRequest(GetSubscriptionHandler.class, request, responseObserver);
    }

    @Override
    public void getSubscriptionsToAccount(BrsApi.GetAccountRequest request, StreamObserver<BrsApi.Subscriptions> responseObserver) {
        handleRequest(GetSubscriptionsToAccountHandler.class, request, responseObserver);
    }

    private class HandlerNotFoundException extends Exception {
        public HandlerNotFoundException(String message) {
            super(message);
        }
    }
}
