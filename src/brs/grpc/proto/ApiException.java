package brs.grpc.proto;

public class ApiException extends Throwable {
    public ApiException(String message) {
        super(message);
    }
}
