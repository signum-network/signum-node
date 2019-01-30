package brs.grpc;

import io.grpc.stub.StreamObserver;

public interface StreamResponseGrpcApiHandler<Request, Response> extends GrpcApiHandler<Request, Response> {

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
            responseObserver.onError(e);
        }
    }
}
