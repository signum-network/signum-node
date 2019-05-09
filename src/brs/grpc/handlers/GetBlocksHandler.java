package brs.grpc.handlers;

import brs.Blockchain;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.BlockService;

public class GetBlocksHandler implements GrpcApiHandler<BrsApi.GetBlocksRequest, BrsApi.Blocks> {

    private final Blockchain blockchain;
    private final BlockService blockService;

    public GetBlocksHandler(Blockchain blockchain, BlockService blockService) {
        this.blockchain = blockchain;
        this.blockService = blockService;
    }

    @Override
    public BrsApi.Blocks handleRequest(BrsApi.GetBlocksRequest request) throws Exception {
        BrsApi.IndexRange indexRange = ProtoBuilder.sanitizeIndexRange(request.getIndexRange());
        int firstIndex = indexRange.getFirstIndex();
        int lastIndex = indexRange.getLastIndex();
        boolean includeTransactions = request.getIncludeTransactions();
        BrsApi.Blocks.Builder builder = BrsApi.Blocks.newBuilder();
        blockchain.getBlocks(firstIndex, lastIndex)
                .forEach(block -> builder.addBlocks(ProtoBuilder.buildBlock(blockchain, blockService, block, includeTransactions)));
        return builder.build();
    }
}
