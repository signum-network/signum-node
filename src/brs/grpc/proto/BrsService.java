package brs.grpc.proto;

import brs.Blockchain;
import brs.BlockchainProcessor;
import brs.Generator;
import brs.grpc.GrpcApiHandler;
import brs.grpc.handlers.GetMiningInfoHandler;
import brs.grpc.handlers.SubmitNonceHandler;
import brs.services.AccountService;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

public class BrsService extends BRSGrpc.BRSImplBase {

    private final GrpcApiHandler<Empty, Brs.MiningInfo> getMiningInfoHandler;
    private final GrpcApiHandler<Brs.SubmitNonceRequest, Brs.SubmitNonceResponse> submitNonceHandler;

    public BrsService(BlockchainProcessor blockchainProcessor, Blockchain blockchain, AccountService accountService, Generator generator) {
        getMiningInfoHandler = new GetMiningInfoHandler(blockchainProcessor);
        submitNonceHandler = new SubmitNonceHandler(blockchain, accountService, generator);
    }

    @Override
    public void getMiningInfo(Empty request, StreamObserver<Brs.MiningInfo> responseObserver) {
        getMiningInfoHandler.handleRequest(request, responseObserver);
    }

    @Override
    public void submitNonce(Brs.SubmitNonceRequest request, StreamObserver<Brs.SubmitNonceResponse> responseObserver) {
        submitNonceHandler.handleRequest(request, responseObserver);
    }
}
