package brs.grpc;

import brs.grpc.proto.ProtoBuilder;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;

public interface StreamResponseGrpcApiHandler<Request extends Message, Response extends Message> extends GrpcApiHandler<Request, Response> {

    @Override
    default Response handleRequest(Request request) {
        throw new UnsupportedOperationException("Cannot return single value from stream response");
    }

    void handleStreamRequest(Request request, StreamObserver<Response> responseObserver) throws Exception;

    @Override
    default void handleRequest(Request request, StreamObserver<Response> responseObserver) {
        try {
            handleStreamRequest(request, responseObserver);
        } catch (Exception e) {
            responseObserver.onError(ProtoBuilder.buildError(e));
        }
    }
}
