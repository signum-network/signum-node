package brs.grpc;

import brs.grpc.proto.ProtoBuilder;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;

public interface GrpcApiHandler<Request extends Message, Response extends Message> {

    /**
     * This should only ever be internally called.
     */
    Response handleRequest(Request request) throws Exception;

    default void handleRequest(Request request, StreamObserver<Response> responseObserver) {
        try {
            responseObserver.onNext(handleRequest(request));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(ProtoBuilder.buildError(e));
        }
    }
}
