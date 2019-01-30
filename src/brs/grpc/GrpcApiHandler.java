package brs.grpc;

import io.grpc.stub.StreamObserver;

public interface GrpcApiHandler<Request, Response> {

    /**
     * This should only ever be internally called.
     */
    Response handleRequest(Request request) throws Exception;

    default void handleRequest(Request request, StreamObserver<Response> responseObserver) {
        try {
            responseObserver.onNext(handleRequest(request));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}
