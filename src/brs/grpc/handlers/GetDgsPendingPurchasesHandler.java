package brs.grpc.handlers;

import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.DGSGoodsStoreService;

public class GetDgsPendingPurchasesHandler implements GrpcApiHandler<BrsApi.GetDgsPendingPurchasesRequest, BrsApi.DgsPurchases> {

    private final DGSGoodsStoreService digitalGoodsStoreService;

    public GetDgsPendingPurchasesHandler(DGSGoodsStoreService digitalGoodsStoreService) {
        this.digitalGoodsStoreService = digitalGoodsStoreService;
    }

    @Override
    public BrsApi.DgsPurchases handleRequest(BrsApi.GetDgsPendingPurchasesRequest request) throws Exception {
        long sellerId = request.getSeller();
        BrsApi.IndexRange indexRange = ProtoBuilder.sanitizeIndexRange(request.getIndexRange());
        int firstIndex = indexRange.getFirstIndex();
        int lastIndex = indexRange.getLastIndex();
        if (sellerId == 0) throw new ApiException("Seller ID not set");
        BrsApi.DgsPurchases.Builder builder = BrsApi.DgsPurchases.newBuilder();
        digitalGoodsStoreService.getPendingSellerPurchases(sellerId, firstIndex, lastIndex)
                .forEach(purchase -> builder.addDgsPurchases(ProtoBuilder.buildPurchase(purchase, digitalGoodsStoreService.getGoods(purchase.getGoodsId()))));
        return builder.build();
    }
}
