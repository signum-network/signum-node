package brs.grpc;

import brs.grpc.proto.ProtoBuilder;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;

public interface GrpcApiHandler<R extends Message, S extends Message> {

    /**
     * This should only ever be internally called.
     */
    S handleRequest(R request) throws Exception;

    default void handleRequest(R request, StreamObserver<S> responseObserver) {
        try {
            responseObserver.onNext(handleRequest(request));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ProtoBuilder.buildError(e));
        }
    }
}
