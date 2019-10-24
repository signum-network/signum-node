package brs.grpc.handlers

import brs.Order
import brs.assetexchange.AssetExchange
import brs.grpc.GrpcApiHandler
import brs.grpc.proto.ApiException
import brs.grpc.proto.BrsApi
import brs.grpc.proto.ProtoBuilder

class GetOrderHandler(private val assetExchange: AssetExchange) : GrpcApiHandler<BrsApi.GetOrderRequest, BrsApi.Order> {

    override fun handleRequest(request: BrsApi.GetOrderRequest): BrsApi.Order {
        val order: Order = when (request.orderType) {
            BrsApi.OrderType.ASK -> assetExchange.getAskOrder(request.orderId)
            BrsApi.OrderType.BID -> assetExchange.getBidOrder(request.orderId)
            else -> throw ApiException("Order type unset")
        }
            ?: throw ApiException("Could not find order")
        return ProtoBuilder.buildOrder(order)
    }
}