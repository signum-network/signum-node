package brs.grpc.handlers;

import brs.DigitalGoodsStore;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.DGSGoodsStoreService;

public class GetDgsGoodHandler implements GrpcApiHandler<BrsApi.GetByIdRequest, BrsApi.DgsGood> {

    private final DGSGoodsStoreService digitalGoodsStoreService;

    public GetDgsGoodHandler(DGSGoodsStoreService digitalGoodsStoreService) {
        this.digitalGoodsStoreService = digitalGoodsStoreService;
    }

    @Override
    public BrsApi.DgsGood handleRequest(BrsApi.GetByIdRequest request) throws Exception {
        long goodsId = request.getId();
        DigitalGoodsStore.Goods goods = digitalGoodsStoreService.getGoods(goodsId);
        if (goods == null) throw new ApiException("Could not find goods");
        return ProtoBuilder.buildGoods(goods);
    }
}
