package brs.grpc.handlers;

import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.services.TimeService;
import com.google.protobuf.Empty;

public class GetCurrentTimeHandler implements GrpcApiHandler<Empty, BrsApi.Time> {

    private final TimeService timeService;

    public GetCurrentTimeHandler(TimeService timeService) {
        this.timeService = timeService;
    }

    @Override
    public BrsApi.Time handleRequest(Empty empty) {
        return BrsApi.Time.newBuilder()
                .setTime(timeService.getEpochTime())
                .build();
    }
}
