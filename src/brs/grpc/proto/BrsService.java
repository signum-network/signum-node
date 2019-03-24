package brs.grpc.proto;

import brs.Blockchain;
import brs.BlockchainProcessor;
import brs.Generator;
import brs.TransactionProcessor;
import brs.grpc.GrpcApiHandler;
import brs.grpc.handlers.*;
import brs.services.AccountService;
import brs.services.BlockService;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BrsService extends BrsApiServiceGrpc.BrsApiServiceImplBase {

    private final Map<Class<? extends GrpcApiHandler<?,?>>, GrpcApiHandler<?,?>> handlers;

    public BrsService(BlockchainProcessor blockchainProcessor, Blockchain blockchain, BlockService blockService, AccountService accountService, Generator generator, TransactionProcessor transactionProcessor) {
        Map<Class<? extends GrpcApiHandler<?,?>>, GrpcApiHandler<?,?>> handlerMap = new HashMap<>();
        handlerMap.put(GetMiningInfoHandler.class, new GetMiningInfoHandler(blockchainProcessor, generator));
        handlerMap.put(SubmitNonceHandler.class, new SubmitNonceHandler(blockchain, accountService, generator));
        handlerMap.put(GetBlockHandler.class, new GetBlockHandler(blockchain, blockService));
        handlerMap.put(GetAccountHandler.class, new GetAccountHandler(accountService));
        handlerMap.put(GetAccountsHandler.class, new GetAccountsHandler(accountService));
        handlerMap.put(GetTransactionHandler.class, new GetTransactionHandler(blockchain, transactionProcessor));
        handlerMap.put(GetTransactionBytesHandler.class, new GetTransactionBytesHandler(blockchain, transactionProcessor));
        this.handlers = Collections.unmodifiableMap(handlerMap);
    }



    private <T extends GrpcApiHandler<S,R>, S, R> void handleRequest(Class<T> handlerClass, S request, StreamObserver<R> response) {
        GrpcApiHandler<?, ?> handler = handlers.get(handlerClass);
        if (handlerClass.isInstance(handler)) {
            T handlerInstance = handlerClass.cast(handler);
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
    public void getTransactionBytes(BrsApi.GetTransactionRequest request, StreamObserver<BrsApi.TransactionBytes> responseObserver) {
        handleRequest(GetTransactionBytesHandler.class, request, responseObserver);
    }

    private class HandlerNotFoundException extends Exception {
        public HandlerNotFoundException(String message) {
            super(message);
        }
    }
}
