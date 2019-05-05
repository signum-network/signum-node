package brs.grpc.handlers;

import brs.Asset;
import brs.Trade;
import brs.assetexchange.AssetExchange;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;

import java.util.Collection;

public class GetAssetTradesHandler implements GrpcApiHandler<BrsApi.GetAssetTransfersRequest, BrsApi.AssetTrades> {

    private final AssetExchange assetExchange;

    public GetAssetTradesHandler(AssetExchange assetExchange) {
        this.assetExchange = assetExchange;
    }

    @Override
    public BrsApi.AssetTrades handleRequest(BrsApi.GetAssetTransfersRequest request) throws Exception {
        long accountId = request.getAccount();
        long assetId = request.getAsset();
        BrsApi.IndexRange indexRange = ProtoBuilder.sanitizeIndexRange(request.getIndexRange());
        int firstIndex = indexRange.getFirstIndex();
        int lastIndex = indexRange.getLastIndex();
        Collection<Trade> trades;
        Asset asset = assetExchange.getAsset(assetId);
        if (accountId == 0) {
            trades = assetExchange.getTrades(assetId, firstIndex, lastIndex);
        } else if (assetId == 0) {
            trades = assetExchange.getAccountTrades(accountId, firstIndex, lastIndex);
        } else {
            trades = assetExchange.getAccountAssetTrades(accountId, assetId, firstIndex, lastIndex);
        }
        BrsApi.AssetTrades.Builder builder = BrsApi.AssetTrades.newBuilder();
        trades.forEach(trade -> builder.addTrades(ProtoBuilder.buildTrade(trade, asset == null ? assetExchange.getAsset(trade.getAssetId()) : asset)));
        return builder.build();
    }
}
