package brs.grpc;

import brs.grpc.proto.ProtoBuilder;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;

public interface StreamResponseGrpcApiHandler<R extends Message, S extends Message> extends GrpcApiHandler<R, S> {

    @Override
    default S handleRequest(R request) {
        throw new UnsupportedOperationException("Cannot return single value from stream response");
    }

    void handleStreamRequest(R request, StreamObserver<S> responseObserver) throws Exception;

    @Override
    default void handleRequest(R request, StreamObserver<S> responseObserver) {
        try {
            handleStreamRequest(request, responseObserver);
        } catch (Exception e) {
            responseObserver.onError(ProtoBuilder.buildError(e));
        }
    }
}
