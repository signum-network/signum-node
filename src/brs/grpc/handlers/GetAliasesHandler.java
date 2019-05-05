package brs.grpc.handlers;

import brs.Alias;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.AliasService;
import brs.util.FilteringIterator;

public class GetAliasesHandler implements GrpcApiHandler<BrsApi.GetAliasesRequest, BrsApi.Aliases> {

    private final AliasService aliasService;

    public GetAliasesHandler(AliasService aliasService) {
        this.aliasService = aliasService;
    }

    @Override
    public BrsApi.Aliases handleRequest(BrsApi.GetAliasesRequest getAliasesRequest) throws Exception {
        final int timestamp = getAliasesRequest.getTimestamp();
        final long accountId = getAliasesRequest.getOwner();
        int firstIndex = getAliasesRequest.getIndexRange().getFirstIndex();
        int lastIndex = getAliasesRequest.getIndexRange().getLastIndex();
        BrsApi.Aliases.Builder aliases = BrsApi.Aliases.newBuilder();
        FilteringIterator<Alias> aliasIterator = new FilteringIterator<>(aliasService.getAliasesByOwner(accountId, 0, -1), alias -> alias.getTimestamp() >= timestamp, firstIndex, lastIndex);
        while (aliasIterator.hasNext()) {
            final Alias alias = aliasIterator.next();
            final Alias.Offer offer = aliasService.getOffer(alias);
            aliases.addAliases(ProtoBuilder.buildAlias(alias, offer));
        }
        return aliases.build();
    }
}
