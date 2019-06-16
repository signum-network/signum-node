package brs.grpc.handlers;

import brs.assetexchange.AssetExchange;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;

public class GetOrdersHandler implements GrpcApiHandler<BrsApi.GetOrdersRequest, BrsApi.Orders> {

    private final AssetExchange assetExchange;

    public GetOrdersHandler(AssetExchange assetExchange) {
        this.assetExchange = assetExchange;
    }

    @Override
    public BrsApi.Orders handleRequest(BrsApi.GetOrdersRequest request) throws Exception {
        BrsApi.Orders.Builder builder = BrsApi.Orders.newBuilder();
        long assetId = request.getAsset();
        BrsApi.IndexRange indexRange = ProtoBuilder.sanitizeIndexRange(request.getIndexRange());
        int firstIndex = indexRange.getFirstIndex();
        int lastIndex = indexRange.getLastIndex();
        if (assetId == 0) {
            // Get all open orders
            switch (request.getOrderType()) {
                case ASK:
                    assetExchange.getAllAskOrders(firstIndex, lastIndex)
                            .forEach(order -> builder.addOrders(ProtoBuilder.buildOrder(order)));
                    break;
                case BID:
                    assetExchange.getAllAskOrders(firstIndex, lastIndex)
                            .forEach(order -> builder.addOrders(ProtoBuilder.buildOrder(order)));
                    break;
                default:
                    throw new ApiException("Order type unset");
            }
        } else {
            // Get orders under that asset
            switch (request.getOrderType()) {
                case ASK:
                    assetExchange.getSortedAskOrders(assetId, firstIndex, lastIndex)
                            .forEach(order -> builder.addOrders(ProtoBuilder.buildOrder(order)));
                    break;
                case BID:
                    assetExchange.getSortedBidOrders(assetId, firstIndex, lastIndex)
                            .forEach(order -> builder.addOrders(ProtoBuilder.buildOrder(order)));
                    break;
                default:
                    throw new ApiException("Order type unset");
            }
        }
        return builder.build();
    }
}
