package brs.grpc.proto;

import brs.Blockchain;
import brs.BlockchainProcessor;
import brs.Generator;
import brs.grpc.GrpcApiHandler;
import brs.grpc.handlers.GetBlockHandler;
import brs.grpc.handlers.GetMiningInfoHandler;
import brs.grpc.handlers.SubmitNonceHandler;
import brs.services.AccountService;
import brs.services.BlockService;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BrsService extends BRSGrpc.BRSImplBase {

    private final Map<Class<? extends GrpcApiHandler<?,?>>, GrpcApiHandler<?,?>> handlers;

    public BrsService(BlockchainProcessor blockchainProcessor, Blockchain blockchain, BlockService blockService, AccountService accountService, Generator generator) {
        Map<Class<? extends GrpcApiHandler<?,?>>, GrpcApiHandler<?,?>> handlers = new HashMap<>();
        handlers.put(GetMiningInfoHandler.class, new GetMiningInfoHandler(blockchainProcessor));
        handlers.put(SubmitNonceHandler.class, new SubmitNonceHandler(blockchain, accountService, generator));
        handlers.put(GetBlockHandler.class, new GetBlockHandler(blockchain, blockService));
        this.handlers = Collections.unmodifiableMap(handlers);
    }

    private <T extends GrpcApiHandler<?,?>> T getHandler(Class<T> handlerClass) throws HandlerNotFoundException {
        GrpcApiHandler<?, ?> handler = handlers.get(handlerClass);
        if (!handlerClass.isInstance(handler)) {
            throw new HandlerNotFoundException();
        }
        return handlerClass.cast(handler);
    }

    @Override
    public void getMiningInfo(Empty request, StreamObserver<Brs.MiningInfo> responseObserver) {
        try {
            getHandler(GetMiningInfoHandler.class).handleRequest(request, responseObserver);
        } catch (HandlerNotFoundException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void submitNonce(Brs.SubmitNonceRequest request, StreamObserver<Brs.SubmitNonceResponse> responseObserver) {
        try {
            getHandler(SubmitNonceHandler.class).handleRequest(request, responseObserver);
        } catch (HandlerNotFoundException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getBlock(Brs.GetBlockRequest request, StreamObserver<Brs.Block> responseObserver) {
        try {
            getHandler(GetBlockHandler.class).handleRequest(request, responseObserver);
        } catch (HandlerNotFoundException e) {
            responseObserver.onError(e);
        }
    }

    private class HandlerNotFoundException extends Exception {}
}
