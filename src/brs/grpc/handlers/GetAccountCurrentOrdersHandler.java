package brs.grpc.handlers;

import brs.assetexchange.AssetExchange;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;

public class GetAccountCurrentOrdersHandler implements GrpcApiHandler<BrsApi.GetAccountOrdersRequest, BrsApi.Orders> {

    private final AssetExchange assetExchange;

    public GetAccountCurrentOrdersHandler(AssetExchange assetExchange) {
        this.assetExchange = assetExchange;
    }

    @Override
    public BrsApi.Orders handleRequest(BrsApi.GetAccountOrdersRequest request) throws Exception {
        long accountId = request.getAccount();
        long assetId = request.getAsset();
        BrsApi.IndexRange indexRange = ProtoBuilder.sanitizeIndexRange(request.getIndexRange());
        int firstIndex = indexRange.getFirstIndex();
        int lastIndex = indexRange.getLastIndex();

        BrsApi.Orders.Builder builder = BrsApi.Orders.newBuilder();
        switch (request.getOrderType()) {
            case ASK:
                (assetId == 0 ? assetExchange.getAskOrdersByAccount(accountId, firstIndex, lastIndex) : assetExchange.getAskOrdersByAccountAsset(accountId, assetId, firstIndex, lastIndex))
                        .forEach(order -> builder.addOrders(ProtoBuilder.buildOrder(order)));
                break;
            case BID:
                (assetId == 0 ? assetExchange.getBidOrdersByAccount(accountId, firstIndex, lastIndex) : assetExchange.getBidOrdersByAccountAsset(accountId, assetId, firstIndex, lastIndex))
                        .forEach(order -> builder.addOrders(ProtoBuilder.buildOrder(order)));
                break;
            default:
                throw new ApiException("Order Type not set");
        }
        return builder.build();
    }
}
