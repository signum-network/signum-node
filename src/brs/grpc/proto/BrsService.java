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

public class BrsService extends BRSGrpc.BRSImplBase {

    private final GrpcApiHandler<Empty, Brs.MiningInfo> getMiningInfoHandler;
    private final GrpcApiHandler<Brs.SubmitNonceRequest, Brs.SubmitNonceResponse> submitNonceHandler;
    private final GrpcApiHandler<Brs.GetBlockRequest, Brs.Block> getBlockHandler;

    public BrsService(BlockchainProcessor blockchainProcessor, Blockchain blockchain, BlockService blockService, AccountService accountService, Generator generator) {
        getMiningInfoHandler = new GetMiningInfoHandler(blockchainProcessor);
        submitNonceHandler = new SubmitNonceHandler(blockchain, accountService, generator);
        getBlockHandler = new GetBlockHandler(blockchain, blockService);
    }

    @Override
    public void getMiningInfo(Empty request, StreamObserver<Brs.MiningInfo> responseObserver) {
        getMiningInfoHandler.handleRequest(request, responseObserver);
    }

    @Override
    public void submitNonce(Brs.SubmitNonceRequest request, StreamObserver<Brs.SubmitNonceResponse> responseObserver) {
        submitNonceHandler.handleRequest(request, responseObserver);
    }

    @Override
    public void getBlock(Brs.GetBlockRequest request, StreamObserver<Brs.Block> responseObserver) {
        getBlockHandler.handleRequest(request, responseObserver);
    }
}
