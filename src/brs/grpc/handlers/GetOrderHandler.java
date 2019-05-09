package brs.grpc.handlers;

import brs.Order;
import brs.assetexchange.AssetExchange;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;

public class GetOrderHandler implements GrpcApiHandler<BrsApi.GetOrderRequest, BrsApi.Order> {

    private final AssetExchange assetExchange;

    public GetOrderHandler(AssetExchange assetExchange) {
        this.assetExchange = assetExchange;
    }

    @Override
    public BrsApi.Order handleRequest(BrsApi.GetOrderRequest request) throws Exception {
        Order order;
        switch (request.getOrderType()) {
            case ASK:
                order = assetExchange.getAskOrder(request.getOrderId());
                break;
            case BID:
                order = assetExchange.getBidOrder(request.getOrderId());
                break;
            default:
                throw new ApiException("Order type unset");
        }
        if (order == null) throw new ApiException("Could not find order");
        return ProtoBuilder.buildOrder(order);
    }
}
