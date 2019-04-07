package brs.grpc.handlers;

import brs.DigitalGoodsStore;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.DGSGoodsStoreService;

public class GetDgsPurchaseHandler implements GrpcApiHandler<BrsApi.GetByIdRequest, BrsApi.DgsPurchase> {

    private final DGSGoodsStoreService digitalGoodsStoreService;

    public GetDgsPurchaseHandler(DGSGoodsStoreService digitalGoodsStoreService) {
        this.digitalGoodsStoreService = digitalGoodsStoreService;
    }

    @Override
    public BrsApi.DgsPurchase handleRequest(BrsApi.GetByIdRequest request) throws Exception {
        DigitalGoodsStore.Purchase purchase = digitalGoodsStoreService.getPurchase(request.getId());
        if (purchase == null) throw new ApiException("Could not find purchase");
        return ProtoBuilder.buildPurchase(purchase, digitalGoodsStoreService.getGoods(purchase.getGoodsId()));
    }
}
