package brs.grpc.handlers;

import brs.Asset;
import brs.assetexchange.AssetExchange;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;

public class GetAssetHandler implements GrpcApiHandler<BrsApi.GetByIdRequest, BrsApi.Asset> {

    private final AssetExchange assetExchange;

    public GetAssetHandler(AssetExchange assetExchange) {
        this.assetExchange = assetExchange;
    }

    @Override
    public BrsApi.Asset handleRequest(BrsApi.GetByIdRequest getByIdRequest) throws Exception {
        Asset asset = assetExchange.getAsset(getByIdRequest.getId());
        if (asset == null) throw new ApiException("Could not find asset");
        return ProtoBuilder.buildAsset(assetExchange, asset);
    }
}
