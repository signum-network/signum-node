package brs.grpc.proto;

import brs.*;
import brs.db.BurstIterator;
import brs.services.AccountService;
import brs.services.BlockService;
import brs.util.Convert;
import com.google.protobuf.ByteString;

import java.util.stream.Collectors;

public final class ProtoBuilder {

    private ProtoBuilder() {
    }

    public static BrsApi.Account buildAccount(Account account, AccountService accountService) {
        BrsApi.Account.Builder builder = BrsApi.Account.newBuilder()
                .setId(account.getId())
                .setPublicKey(ByteString.copyFrom(account.getPublicKey()))
                .setBalance(account.getBalanceNQT())
                .setUnconfirmedBalance(account.getUnconfirmedBalanceNQT())
                .setForgedBalance(account.getForgedBalanceNQT())
                .setName(account.getName())
                .setDescription(account.getDescription())
                .setRewardRecipient(accountService.getRewardRecipientAssignment(account).accountId);

        try (BurstIterator<Account.AccountAsset> assets = accountService.getAssets(account.id, 0, -1)) {
            assets.forEachRemaining(asset -> builder.addAssetBalances(buildAssetBalance(asset)));
        }

        return builder.build();
    }

    private static BrsApi.AssetBalance buildAssetBalance(Account.AccountAsset asset) {
        return BrsApi.AssetBalance.newBuilder()
            .setId(asset.getAssetId())
            .setBalance(asset.getQuantityQNT())
            .setUnconfirmedBalance(asset.getUnconfirmedQuantityQNT())
            .build();
    }

    public static BrsApi.Block buildBlock(Blockchain blockchain, BlockService blockService, Block block, boolean includeTransactions) {
        BrsApi.Block.Builder builder = BrsApi.Block.newBuilder()
                .setId(block.getId())
                .setHeight(block.getHeight())
                .setNumberOfTransactions(block.getTransactions().size())
                .setTotalAmount(block.getTotalAmountNQT())
                .setTotalFee(block.getTotalFeeNQT())
                .setBlockReward(blockService.getBlockReward(block) * Constants.ONE_BURST)
                .setPayloadLength(block.getPayloadLength())
                .setVersion(block.getVersion())
                .setBaseTarget(block.getBaseTarget())
                .setTimestamp(block.getTimestamp())
                .setGenerationSignature(ByteString.copyFrom(block.getGenerationSignature()))
                .setBlockSignature(ByteString.copyFrom(block.getBlockSignature()))
                .setPayloadHash(ByteString.copyFrom(block.getPayloadHash()))
                .setGeneratorPublicKey(ByteString.copyFrom(block.getGeneratorPublicKey()))
                .setNonce(block.getNonce())
                .setScoop(blockService.getScoopNum(block))
                .setPreviousBlock(block.getPreviousBlockId())
                .setNextBlock(block.getNextBlockId())
                .setPreviousBlockHash(ByteString.copyFrom(block.getPreviousBlockHash()));

        if (includeTransactions) {
            builder.addAllTransactions(block.getTransactions().stream()
                    .map(transaction -> buildTransaction(blockchain, transaction))
                    .collect(Collectors.toList()));
        } else {
            builder.addAllTransactionIds(block.getTransactions().stream()
                    .map(Transaction::getId)
                    .collect(Collectors.toList()));
        }
        return builder.build();
    }

    public static BrsApi.Transaction buildTransaction(Blockchain blockchain, Transaction transaction) {
        BrsApi.Transaction.Builder builder = BrsApi.Transaction.newBuilder()
                .setId(transaction.getId())
                .setVersion(transaction.getVersion())
                .setType(transaction.getType().getType())
                .setSubtype(transaction.getType().getSubtype())
                .setTimestamp(transaction.getTimestamp())
                .setDeadline(transaction.getDeadline())
                .setSender(ByteString.copyFrom(transaction.getSenderPublicKey()))
                .setRecipient(transaction.getRecipientId())
                .setAmount(transaction.getAmountNQT())
                .setFee(transaction.getFeeNQT())
                .setBlock(transaction.getBlockId())
                .setBlockHeight(transaction.getHeight())
                .setBlockTimestamp(transaction.getBlockTimestamp())
                .setSignature(ByteString.copyFrom(transaction.getSignature()))
                .setReferencedTransactionFullHash(ByteString.copyFrom(Convert.parseHexString(transaction.getReferencedTransactionFullHash())))
                .setFullHash(ByteString.copyFrom(Convert.parseHexString(transaction.getFullHash())))
                .setConfirmations(blockchain.getHeight() - transaction.getHeight())
                .setEcBlockId(transaction.getECBlockId())
                .setEcBlockHeight(transaction.getECBlockHeight())
                .addAllAppendices(transaction.getAppendages().stream()
                        .map(Appendix::getProtobufMessage)
                        .collect(Collectors.toList()));

        return builder.build();
    }
}
