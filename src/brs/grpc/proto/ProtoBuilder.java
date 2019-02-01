package brs.grpc.proto;

import brs.*;
import brs.services.BlockService;
import brs.util.Convert;
import com.google.protobuf.ByteString;

import java.util.stream.Collectors;

public class ProtoBuilder {
    public static Brs.Block buildBlock(Blockchain blockchain, BlockService blockService, Block block, boolean includeTransactions) {
        Brs.Block.Builder builder = Brs.Block.newBuilder()
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

    public static Brs.Transaction buildTransaction(Blockchain blockchain, Transaction transaction) {
        Brs.Transaction.Builder builder = Brs.Transaction.newBuilder()
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
