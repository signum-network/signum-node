package brs.grpc.handlers;

import brs.Account;
import brs.Blockchain;
import brs.Escrow;
import brs.Generator;
import brs.assetexchange.AssetExchange;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.peer.Peers;
import brs.services.AccountService;
import brs.services.AliasService;
import brs.services.EscrowService;
import com.google.protobuf.Empty;

public class GetCountsHandler implements GrpcApiHandler<Empty, BrsApi.Counts> {
    
    private final AccountService accountService;
    private final EscrowService escrowService;
    private final Blockchain blockchain;
    private final AssetExchange assetExchange;
    private final AliasService aliasService;
    private final Generator generator;

    public GetCountsHandler(AccountService accountService, EscrowService escrowService, Blockchain blockchain, AssetExchange assetExchange, AliasService aliasService, Generator generator) {
        this.accountService = accountService;
        this.escrowService = escrowService;
        this.blockchain = blockchain;
        this.assetExchange = assetExchange;
        this.aliasService = aliasService;
        this.generator = generator;
    }

    @Override
    public BrsApi.Counts handleRequest(Empty empty) throws Exception {
        long totalEffectiveBalance = 0;
        int numberOfBlocks = blockchain.getHeight() + 1; // Height + genesis
        int numberOfTransactions = blockchain.getTransactionCount();
        int numberOfAccounts = accountService.getCount();
        int numberOfAssets = assetExchange.getAssetsCount();
        int numberOfAskOrders = assetExchange.getAskCount();
        int numberOfBidOrders = assetExchange.getBidCount();
        int numberOfOrders = numberOfAskOrders + numberOfBidOrders;
        int numberOfTrades = assetExchange.getTradesCount();
        int numberOfTransfers = assetExchange.getAssetTransferCount();
        long numberOfAliases = aliasService.getAliasCount();
        int numberOfPeers = Peers.getAllPeers().size();
        int numberOfGenerators = generator.getAllGenerators().size();
        for (Account account : accountService.getAllAccounts(0, -1)) {
            long effectiveBalanceBURST = account.getBalanceNQT();
            if (effectiveBalanceBURST > 0) {
                totalEffectiveBalance += effectiveBalanceBURST;
            }
        }
        for (Escrow escrow : escrowService.getAllEscrowTransactions()) {
            totalEffectiveBalance += escrow.getAmountNQT();
        }

        return BrsApi.Counts.newBuilder()
                .setNumberOfBlocks(numberOfBlocks)
                .setNumberOfTransactions(numberOfTransactions)
                .setNumberOfAccounts(numberOfAccounts)
                .setNumberOfAssets(numberOfAssets)
                .setNumberOfOrders(numberOfOrders)
                .setNumberOfAskOrders(numberOfAskOrders)
                .setNumberOfBidOrders(numberOfBidOrders)
                .setNumberOfTrades(numberOfTrades)
                .setNumberOfTransfers(numberOfTransfers)
                .setNumberOfAliases(numberOfAliases)
                .setNumberOfPeers(numberOfPeers)
                .setNumberOfGenerators(numberOfGenerators)
                .setTotalEffectiveBalance(totalEffectiveBalance)
                .build();
    }
}
