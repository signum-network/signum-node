package brs.grpc.handlers;

import brs.Block;
import brs.Blockchain;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.BlockService;

public class GetBlockHandler implements GrpcApiHandler<BrsApi.GetBlockRequest, BrsApi.Block> {

    private final Blockchain blockchain;
    private final BlockService blockService;

    public GetBlockHandler(Blockchain blockchain, BlockService blockService) {
        this.blockchain = blockchain;
        this.blockService = blockService;
    }

    @Override
    public BrsApi.Block handleRequest(BrsApi.GetBlockRequest request) throws Exception {
        long blockId = request.getBlockId();
        int blockHeight = request.getHeight();
        int timestamp = request.getTimestamp();

        Block block;
        if (blockId > 0) {
            try {
                block = blockchain.getBlock(blockId);
            } catch (RuntimeException e) {
                throw new ApiException("Incorrect Block ID");
            }
        } else if (blockHeight > 0) {
            try {
                if (blockHeight > blockchain.getHeight()) {
                    throw new ApiException("Incorrect Block Height");
                }
                block = blockchain.getBlockAtHeight(blockHeight);
            } catch (RuntimeException e) {
                throw new ApiException("Incorrect Block Height");
            }
        } else if (timestamp > 0) {
            try {
                block = blockchain.getLastBlock(timestamp);
            } catch (RuntimeException e) {
                throw new ApiException("Incorrect Timestamp");
            }
        } else {
            block = blockchain.getLastBlock();
        }

        if (block == null) {
            throw new ApiException("Unknown Block");
        }

        boolean includeTransactions = request.getIncludeTransactions();

        return ProtoBuilder.buildBlock(blockchain, blockService, block, includeTransactions);
    }
}
