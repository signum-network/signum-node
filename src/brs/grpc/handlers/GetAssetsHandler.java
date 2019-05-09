package brs.grpc.handlers;

import brs.Asset;
import brs.assetexchange.AssetExchange;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;

public class GetAssetsHandler implements GrpcApiHandler<BrsApi.GetAssetsRequest, BrsApi.Assets> {

    private final AssetExchange assetExchange;

    public GetAssetsHandler(AssetExchange assetExchange) {
        this.assetExchange = assetExchange;
    }

    @Override
    public BrsApi.Assets handleRequest(BrsApi.GetAssetsRequest getAssetsRequest) throws Exception {
        BrsApi.Assets.Builder builder = BrsApi.Assets.newBuilder();
        getAssetsRequest.getAssetList().forEach(assetId -> {
            Asset asset = assetExchange.getAsset(assetId);
            if (asset == null) return;
            builder.addAssets(ProtoBuilder.buildAsset(assetExchange, asset));
        });
        return builder.build();
    }
}
