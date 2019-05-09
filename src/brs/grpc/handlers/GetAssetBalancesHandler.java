package brs.grpc.handlers;

import brs.Asset;
import brs.assetexchange.AssetExchange;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;

public class GetAssetBalancesHandler implements GrpcApiHandler<BrsApi.GetAssetBalancesRequest, BrsApi.AssetBalances> {

    private final AssetExchange assetExchange;

    public GetAssetBalancesHandler(AssetExchange assetExchange) {
        this.assetExchange = assetExchange;
    }

    @Override
    public BrsApi.AssetBalances handleRequest(BrsApi.GetAssetBalancesRequest request) throws Exception {
        long assetId = request.getAsset();
        BrsApi.IndexRange indexRange = ProtoBuilder.sanitizeIndexRange(request.getIndexRange());
        int firstIndex = indexRange.getFirstIndex();
        int lastIndex = indexRange.getLastIndex();
        int height = request.getHeight();

        Asset asset = assetExchange.getAsset(assetId);
        if (asset == null) throw new ApiException("Could not find asset");

        BrsApi.AssetBalances.Builder builder = BrsApi.AssetBalances.newBuilder();

        assetExchange.getAccountAssetsOverview(asset.getId(), height, firstIndex, lastIndex)
                .forEach(assetAccount -> builder.addAssetBalances(ProtoBuilder.buildAssetBalance(assetAccount)));

        return builder.build();
    }
}
