package brs.grpc.handlers;

import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.DGSGoodsStoreService;

public class GetDgsGoodsHandler implements GrpcApiHandler<BrsApi.GetDgsGoodsRequest, BrsApi.DgsGoods> {

    private final DGSGoodsStoreService digitalGoodsStoreService;

    public GetDgsGoodsHandler(DGSGoodsStoreService digitalGoodsStoreService) {
        this.digitalGoodsStoreService = digitalGoodsStoreService;
    }

    @Override
    public BrsApi.DgsGoods handleRequest(BrsApi.GetDgsGoodsRequest request) throws Exception {
        long sellerId = request.getSeller();
        boolean inStockOnly = request.getInStockOnly();
        BrsApi.IndexRange indexRange = ProtoBuilder.sanitizeIndexRange(request.getIndexRange());
        int firstIndex = indexRange.getFirstIndex();
        int lastIndex = indexRange.getLastIndex();
        BrsApi.DgsGoods.Builder builder = BrsApi.DgsGoods.newBuilder();
        if (sellerId == 0) {
            (inStockOnly ? digitalGoodsStoreService.getGoodsInStock(firstIndex, lastIndex) : digitalGoodsStoreService.getAllGoods(firstIndex, lastIndex))
                    .forEach(goods -> builder.addGoods(ProtoBuilder.buildGoods(goods)));
        } else {
            digitalGoodsStoreService.getSellerGoods(sellerId, inStockOnly, firstIndex, lastIndex)
                    .forEach(goods -> builder.addGoods(ProtoBuilder.buildGoods(goods)));
        }
        return builder.build();
    }
}
