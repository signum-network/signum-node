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
        handlerMap.put(GetMiningInfoHandler.class, new GetMiningInfoHandler(blockchainProcessor));
        handlerMap.put(SubmitNonceHandler.class, new SubmitNonceHandler(blockchain, accountService, generator));
        handlerMap.put(GetBlockHandler.class, new GetBlockHandler(blockchain, blockService));
        handlerMap.put(GetAccountHandler.class, new GetAccountHandler(accountService));
        handlerMap.put(GetAccountsHandler.class, new GetAccountsHandler(accountService));
        handlerMap.put(GetTransactionHandler.class, new GetTransactionHandler(blockchain, transactionProcessor));
        handlerMap.put(GetTransactionBytesHandler.class, new GetTransactionBytesHandler(blockchain, transactionProcessor));
        this.handlers = Collections.unmodifiableMap(handlerMap);
    }

    private <T extends GrpcApiHandler<?,?>> T getHandler(Class<T> handlerClass) throws HandlerNotFoundException {
        GrpcApiHandler<?, ?> handler = handlers.get(handlerClass);
        if (!handlerClass.isInstance(handler)) {
            throw new HandlerNotFoundException();
        }
        return handlerClass.cast(handler);
    }

    @Override
    public void getMiningInfo(Empty request, StreamObserver<BrsApi.MiningInfo> responseObserver) {
        try {
            getHandler(GetMiningInfoHandler.class).handleRequest(request, responseObserver);
        } catch (HandlerNotFoundException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void submitNonce(BrsApi.SubmitNonceRequest request, StreamObserver<BrsApi.SubmitNonceResponse> responseObserver) {
        try {
            getHandler(SubmitNonceHandler.class).handleRequest(request, responseObserver);
        } catch (HandlerNotFoundException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getAccount(BrsApi.GetAccountRequest request, StreamObserver<BrsApi.Account> responseObserver) {
        try {
            getHandler(GetAccountHandler.class).handleRequest(request, responseObserver);
        } catch (HandlerNotFoundException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getAccounts(BrsApi.GetAccountsRequest request, StreamObserver<BrsApi.Accounts> responseObserver) {
        try {
            getHandler(GetAccountsHandler.class).handleRequest(request, responseObserver);
        } catch (HandlerNotFoundException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getBlock(BrsApi.GetBlockRequest request, StreamObserver<BrsApi.Block> responseObserver) {
        try {
            getHandler(GetBlockHandler.class).handleRequest(request, responseObserver);
        } catch (HandlerNotFoundException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getTransaction(BrsApi.GetTransactionRequest request, StreamObserver<BrsApi.Transaction> responseObserver) {
        try {
            getHandler(GetTransactionHandler.class).handleRequest(request, responseObserver);
        } catch (HandlerNotFoundException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getTransactionBytes(BrsApi.GetTransactionRequest request, StreamObserver<BrsApi.TransactionBytes> responseObserver) {
        try {
            getHandler(GetTransactionBytesHandler.class).handleRequest(request, responseObserver);
        } catch (HandlerNotFoundException e) {
            responseObserver.onError(e);
        }
    }

    private class HandlerNotFoundException extends Exception {}
}
