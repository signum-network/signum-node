package brs.grpc.proto;

import brs.Blockchain;
import brs.grpc.GrpcApiHandler;
import brs.grpc.handlers.GetMiningInfoHandler;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

public class BrsService extends BRSGrpc.BRSImplBase {

    private final GrpcApiHandler<Empty, Brs.MiningInfo> getMiningInfoHandler;

    public BrsService(Blockchain blockchain) {
        getMiningInfoHandler = new GetMiningInfoHandler(blockchain);
    }

    @Override
    public void getMiningInfo(Empty request, StreamObserver<Brs.MiningInfo> responseObserver) {
        getMiningInfoHandler.handleRequest(request, responseObserver);
    }
}
