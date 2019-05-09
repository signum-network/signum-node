package brs.grpc.handlers;

import brs.Subscription;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.SubscriptionService;

public class GetSubscriptionHandler implements GrpcApiHandler<BrsApi.GetByIdRequest, BrsApi.Subscription> {

    private final SubscriptionService subscriptionService;

    public GetSubscriptionHandler(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public BrsApi.Subscription handleRequest(BrsApi.GetByIdRequest request) throws Exception {
        long subscriptionId = request.getId();
        Subscription subscription = subscriptionService.getSubscription(subscriptionId);
        if (subscription == null) throw new ApiException("Could not find subscription");
        return ProtoBuilder.buildSubscription(subscription);
    }
}
