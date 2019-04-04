package brs.grpc.handlers;

import brs.Account;
import brs.Block;
import brs.Blockchain;
import brs.db.BurstIterator;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.AccountService;
import brs.services.BlockService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GetAccountBlocksHandler implements GrpcApiHandler<BrsApi.GetAccountBlocksRequest, BrsApi.Blocks> {

    private final Blockchain blockchain;
    private final BlockService blockService;
    private final AccountService accountService;

    public GetAccountBlocksHandler(Blockchain blockchain, BlockService blockService, AccountService accountService) {
        this.blockchain = blockchain;
        this.blockService = blockService;
        this.accountService = accountService;
    }

    @Override
    public BrsApi.Blocks handleRequest(BrsApi.GetAccountBlocksRequest getAccountRequest) throws Exception {
        long accountId = getAccountRequest.getAccountId();
        int timestamp = getAccountRequest.getTimestamp();
        boolean includeTransactions = getAccountRequest.getIncludeTransactions();

        BrsApi.IndexRange indexRange = ProtoBuilder.sanitizeIndexRange(getAccountRequest.getIndexRange());
        int firstIndex = indexRange.getFirstIndex();
        int lastIndex = indexRange.getLastIndex();

        Account account = accountService.getAccount(accountId);
        if (account == null) throw new ApiException("Could not find account");

        List<Block> blocks = new ArrayList<>();
        try (BurstIterator<? extends Block> iterator = blockchain.getBlocks(account, timestamp, firstIndex, lastIndex)) {
            iterator.forEachRemaining(blocks::add);
        }

        return BrsApi.Blocks.newBuilder()
                .addAllBlocks(blocks.stream()
                        .map(block -> ProtoBuilder.buildBlock(blockchain, blockService, block, includeTransactions))
                        .collect(Collectors.toList()))
                .build();
    }
}
