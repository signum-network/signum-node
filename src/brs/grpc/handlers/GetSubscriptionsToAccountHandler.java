package brs.grpc.handlers;

import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.SubscriptionService;

public class GetSubscriptionsToAccountHandler implements GrpcApiHandler<BrsApi.GetAccountRequest, BrsApi.Subscriptions> {

    private final SubscriptionService subscriptionService;

    public GetSubscriptionsToAccountHandler(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    public BrsApi.Subscriptions handleRequest(BrsApi.GetAccountRequest request) throws Exception {
        long accountId = request.getAccountId();
        BrsApi.Subscriptions.Builder builder = BrsApi.Subscriptions.newBuilder();
        subscriptionService.getSubscriptionsToId(accountId)
                .forEach(subscription -> builder.addSubscriptions(ProtoBuilder.buildSubscription(subscription)));
        return builder.build();
    }
}
