package brs.grpc.handlers

import brs.assetexchange.AssetExchange
import brs.grpc.GrpcApiHandler
import brs.grpc.proto.ApiException
import brs.grpc.proto.BrsApi
import brs.grpc.proto.ProtoBuilder

class GetAssetHandler(private val assetExchange: AssetExchange) : GrpcApiHandler<BrsApi.GetByIdRequest, BrsApi.Asset> {

    override fun handleRequest(getByIdRequest: BrsApi.GetByIdRequest): BrsApi.Asset {
        val asset = assetExchange.getAsset(getByIdRequest.id) ?: throw ApiException("Could not find asset")
        return ProtoBuilder.buildAsset(assetExchange, asset)
    }
}