package brs.grpc.handlers;

import brs.DigitalGoodsStore;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.DGSGoodsStoreService;
import brs.util.FilteringIterator;

import java.util.Collection;

public class GetDgsPurchasesHandler implements GrpcApiHandler<BrsApi.GetDgsPurchasesRequest, BrsApi.DgsPurchases> {
    
    private final DGSGoodsStoreService digitalGoodsStoreService;

    public GetDgsPurchasesHandler(DGSGoodsStoreService digitalGoodsStoreService) {
        this.digitalGoodsStoreService = digitalGoodsStoreService;
    }

    @Override
    public BrsApi.DgsPurchases handleRequest(BrsApi.GetDgsPurchasesRequest request) throws Exception {
        long sellerId = request.getSeller();
        long buyerId = request.getBuyer();
        BrsApi.IndexRange indexRange = ProtoBuilder.sanitizeIndexRange(request.getIndexRange());
        int firstIndex = indexRange.getFirstIndex();
        int lastIndex = indexRange.getLastIndex();
        boolean completed = request.getCompleted();


        Collection<DigitalGoodsStore.Purchase> purchases;
        if (sellerId == 0 && buyerId == 0) {
            purchases = digitalGoodsStoreService.getAllPurchases(firstIndex, lastIndex);
        } else if (sellerId != 0 && buyerId == 0) {
            purchases = digitalGoodsStoreService.getSellerPurchases(sellerId, firstIndex, lastIndex);
        } else if (sellerId == 0) {
            purchases = digitalGoodsStoreService.getBuyerPurchases(buyerId, firstIndex, lastIndex);
        } else {
            purchases = digitalGoodsStoreService.getSellerBuyerPurchases(sellerId, buyerId, firstIndex, lastIndex);
        }

        BrsApi.DgsPurchases.Builder builder = BrsApi.DgsPurchases.newBuilder();
        new FilteringIterator<>(purchases, purchase -> ! (completed && purchase.isPending()), firstIndex, lastIndex)
                .forEachRemaining(purchase -> builder.addDgsPurchases(ProtoBuilder.buildPurchase(purchase, digitalGoodsStoreService.getGoods(purchase.getGoodsId()))));
        return builder.build();
    }
}
