package brs.grpc.handlers;

import brs.Asset;
import brs.AssetTransfer;
import brs.assetexchange.AssetExchange;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.AccountService;

import java.util.Collection;

public class GetAssetTransfersHandler implements GrpcApiHandler<BrsApi.GetAssetTransfersRequest, BrsApi.AssetTransfers> {

    private final AssetExchange assetExchange;
    private final AccountService accountService;

    public GetAssetTransfersHandler(AssetExchange assetExchange, AccountService accountService) {
        this.assetExchange = assetExchange;
        this.accountService = accountService;
    }

    @Override
    public BrsApi.AssetTransfers handleRequest(BrsApi.GetAssetTransfersRequest request) throws Exception {
        long accountId = request.getAccount();
        long assetId = request.getAsset();
        BrsApi.IndexRange indexRange = ProtoBuilder.sanitizeIndexRange(request.getIndexRange());
        int firstIndex = indexRange.getFirstIndex();
        int lastIndex = indexRange.getLastIndex();
        Collection<AssetTransfer> transfers;
        Asset asset = assetExchange.getAsset(assetId);
        if (accountId == 0) {
            transfers = assetExchange.getAssetTransfers(asset.getId(), firstIndex, lastIndex);
        } else if (assetId == 0) {
            transfers = accountService.getAssetTransfers(accountId, firstIndex, lastIndex);
        } else {
            transfers = assetExchange.getAccountAssetTransfers(accountId, assetId, firstIndex, lastIndex);
        }
        BrsApi.AssetTransfers.Builder builder = BrsApi.AssetTransfers.newBuilder();
        transfers.forEach(transfer -> builder.addAssetTransfers(ProtoBuilder.buildTransfer(transfer, asset == null ? assetExchange.getAsset(transfer.getAssetId()) : asset)));
        return builder.build();
    }
}
