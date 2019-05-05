package brs.grpc.handlers;

import brs.assetexchange.AssetExchange;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;

public class GetAssetsByIssuerHandler implements GrpcApiHandler<BrsApi.GetAccountRequest, BrsApi.Assets> {

    private final AssetExchange assetExchange;

    public GetAssetsByIssuerHandler(AssetExchange assetExchange) {
        this.assetExchange = assetExchange;
    }

    @Override
    public BrsApi.Assets handleRequest(BrsApi.GetAccountRequest getAccountRequest) throws Exception {
        BrsApi.Assets.Builder builder = BrsApi.Assets.newBuilder();
        assetExchange.getAssetsIssuedBy(getAccountRequest.getAccountId(), 0, -1)
                .forEach(asset -> builder.addAssets(ProtoBuilder.buildAsset(assetExchange, asset)));
        return builder.build();
    }
}
