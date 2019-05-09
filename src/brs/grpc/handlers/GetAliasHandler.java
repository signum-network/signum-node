package brs.grpc.handlers;

import brs.Alias;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.AliasService;

public class GetAliasHandler implements GrpcApiHandler<BrsApi.GetAliasRequest, BrsApi.Alias> {

    private final AliasService aliasService;

    public GetAliasHandler(AliasService aliasService) {
        this.aliasService = aliasService;
    }

    @Override
    public BrsApi.Alias handleRequest(BrsApi.GetAliasRequest getAliasRequest) throws Exception {
        Alias alias = getAliasRequest.getName().equals("") ? aliasService.getAlias(getAliasRequest.getId()) : aliasService.getAlias(getAliasRequest.getName());
        if (alias == null) throw new ApiException("Alias not found");
        return ProtoBuilder.buildAlias(alias, aliasService.getOffer(alias));
    }
}
