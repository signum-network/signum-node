package brs.grpc.proto;

import brs.*;
import brs.assetexchange.AssetExchange;
import brs.crypto.EncryptedData;
import brs.services.AccountService;
import brs.services.BlockService;
import brs.util.Convert;
import com.google.protobuf.ByteString;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.StatusException;
import io.grpc.protobuf.StatusProto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.stream.Collectors;

public final class ProtoBuilder {

    private ProtoBuilder() {
    }

    public static StatusException buildError(Throwable t) {
        return StatusProto.toStatusException(Status.newBuilder().setCode(Code.ABORTED_VALUE).setMessage(t.getMessage()).build());
    }

    public static BrsApi.Account buildAccount(Account account, AccountService accountService) {
        return BrsApi.Account.newBuilder()
                .setId(account.getId())
                .setPublicKey(ByteString.copyFrom(account.getPublicKey()))
                .setBalance(account.getBalanceNQT())
                .setUnconfirmedBalance(account.getUnconfirmedBalanceNQT())
                .setForgedBalance(account.getForgedBalanceNQT())
                .setName(account.getName())
                .setDescription(account.getDescription())
                .setRewardRecipient(accountService.getRewardRecipientAssignment(account).accountId)
                .addAllAssetBalances(accountService.getAssets(account.id, 0, -1)
                        .stream()
                        .map(ProtoBuilder::buildAssetBalance)
                        .collect(Collectors.toList()))
                .build();
    }

    public static BrsApi.AssetBalance buildAssetBalance(Account.AccountAsset asset) {
        return BrsApi.AssetBalance.newBuilder()
                .setAsset(asset.getAssetId())
                .setAccount(asset.getAccountId())
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
                .addAllTransactionIds(block.getTransactions().stream()
                        .map(Transaction::getId)
                        .collect(Collectors.toList()))
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
            int currentHeight = blockchain.getHeight();
            builder.addAllTransactions(block.getTransactions().stream()
                    .map(transaction -> buildTransaction(transaction, currentHeight))
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    public static BrsApi.BasicTransaction buildBasicTransaction(Transaction transaction) {
        BrsApi.BasicTransaction.Builder builder = BrsApi.BasicTransaction.newBuilder()
                .setSender(ByteString.copyFrom(transaction.getSenderPublicKey()))
                .setRecipient(transaction.getRecipientId())
                .setVersion(transaction.getVersion())
                .setType(transaction.getType().getType())
                .setSubtype(transaction.getType().getSubtype())
                .setAmount(transaction.getAmountNQT())
                .setFee(transaction.getFeeNQT())
                .setTimestamp(transaction.getTimestamp())
                .setDeadline(transaction.getDeadline())
                .setReferencedTransactionFullHash(ByteString.copyFrom(Convert.parseHexString(transaction.getReferencedTransactionFullHash())))
                .addAllAppendages(transaction.getAppendages()
                        .stream()
                        .map(Appendix::getProtobufMessage)
                        .collect(Collectors.toList()));
        if (transaction.getAttachment() != null) {
            builder.setAttachment(transaction.getAttachment().getProtobufMessage());
        }
        return builder.build();
    }

    public static BrsApi.Transaction buildTransaction(Transaction transaction, int currentHeight) {
        return BrsApi.Transaction.newBuilder()
                .setTransaction(buildBasicTransaction(transaction))
                .setId(transaction.getId())
                .setTransactionBytes(ByteString.copyFrom(transaction.getBytes()))
                .setBlock(transaction.getBlockId())
                .setBlockHeight(transaction.getHeight())
                .setBlockTimestamp(transaction.getBlockTimestamp())
                .setSignature(ByteString.copyFrom(transaction.getSignature()))
                .setFullHash(ByteString.copyFrom(Convert.parseHexString(transaction.getFullHash())))
                .setConfirmations(currentHeight - transaction.getHeight())
                .setEcBlockId(transaction.getECBlockId())
                .setEcBlockHeight(transaction.getECBlockHeight())
                .build();
    }

    public static BrsApi.Transaction buildUnconfirmedTransaction(Transaction transaction) {return BrsApi.Transaction.newBuilder()
            .setTransaction(buildBasicTransaction(transaction))
            .setId(transaction.getId())
            .setTransactionBytes(ByteString.copyFrom(transaction.getBytes()))
            .setBlockHeight(transaction.getHeight())
            .setSignature(ByteString.copyFrom(transaction.getSignature()))
            .setFullHash(ByteString.copyFrom(Convert.parseHexString(transaction.getFullHash())))
            .setEcBlockId(transaction.getECBlockId())
            .setEcBlockHeight(transaction.getECBlockHeight())
            .build();
    }

    public static BrsApi.AT buildAT(AccountService accountService, AT at) {
        ByteBuffer bf = ByteBuffer.allocate( 8 );
        bf.order( ByteOrder.LITTLE_ENDIAN );
        bf.put( at.getCreator() );
        bf.clear();
        long creatorId = bf.getLong(); // TODO can this be improved?
        bf.clear();
        bf.put( at.getId() , 0 , 8 );
        long atId = bf.getLong(0);
        return BrsApi.AT.newBuilder()
                .setId(atId)
                .setCreator(creatorId)
                .setVersion(at.getVersion())
                .setName(at.getName())
                .setDescription(at.getDescription())
                .setMachineCode(ByteString.copyFrom(at.getApCode()))
                .setMachineData(ByteString.copyFrom(at.getApData()))
                .setBalance(accountService.getAccount(atId).getBalanceNQT())
                .setPreviousBalance(at.getP_balance())
                .setNextBlock(at.nextHeight())
                .setFrozen(at.freezeOnSameBalance())
                .setRunning(at.getMachineState().isRunning())
                .setStopped(at.getMachineState().isStopped())
                .setFinished(at.getMachineState().isFinished())
                .setDead(at.getMachineState().isDead())
                .setMinActivation(at.minActivationAmount())
                .setCreationBlock(at.getCreationBlockHeight())
                .build();
    }

    public static BrsApi.Alias buildAlias(Alias alias, Alias.Offer offer) {
        BrsApi.Alias.Builder builder = BrsApi.Alias.newBuilder()
                .setId(alias.getId())
                .setOwner(alias.getAccountId())
                .setName(alias.getAliasName())
                .setUri(alias.getAliasURI())
                .setTimestamp(alias.getTimestamp())
                .setOffered(offer != null);

        if (offer != null) {
            builder.setPrice(offer.getPriceNQT());
            builder.setBuyer(offer.getBuyerId());
        }

        return builder.build();
    }

    public static BrsApi.EncryptedData buildEncryptedData(EncryptedData encryptedData) {
        if (encryptedData == null) return BrsApi.EncryptedData.getDefaultInstance(); // TODO is this needed for all methods?
        return BrsApi.EncryptedData.newBuilder()
                .setData(ByteString.copyFrom(encryptedData.getData()))
                .setNonce(ByteString.copyFrom(encryptedData.getNonce()))
                .build();
    }

    public static EncryptedData parseEncryptedData(BrsApi.EncryptedData encryptedData) {
        return new EncryptedData(encryptedData.getData().toByteArray(), encryptedData.getNonce().toByteArray());
    }

    public static BrsApi.IndexRange sanitizeIndexRange(BrsApi.IndexRange indexRange) {
        BrsApi.IndexRange.Builder newIndexRange = indexRange.toBuilder();
        if (newIndexRange.getFirstIndex() == 0 && newIndexRange.getLastIndex() == 0) { // Unset values
            newIndexRange.setLastIndex(Integer.MAX_VALUE); // Signed :(
        }
        if (newIndexRange.getFirstIndex() < 0 || newIndexRange.getLastIndex() < 0) {
            newIndexRange.setFirstIndex(0);
            newIndexRange.setLastIndex(100);
        }
        if (newIndexRange.getFirstIndex() > newIndexRange.getLastIndex()) {
            newIndexRange.setFirstIndex(newIndexRange.getLastIndex());
        }
        return newIndexRange.build();
    }

    public static BrsApi.Asset buildAsset(AssetExchange assetExchange, Asset asset) {
        return BrsApi.Asset.newBuilder()
                .setAsset(asset.getId())
                .setAccount(asset.getAccountId())
                .setName(asset.getName())
                .setDescription(asset.getDescription())
                .setQuantity(asset.getQuantityQNT())
                .setDecimals(asset.getDecimals())
                .setNumberOfTrades(assetExchange.getTradeCount(asset.getId()))
                .setNumberOfTransfers(assetExchange.getTransferCount(asset.getId()))
                .setNumberOfAccounts(assetExchange.getAssetAccountsCount(asset.getId()))
                .build();
    }

    public static BrsApi.Subscription buildSubscription(Subscription subscription) {
        return BrsApi.Subscription.newBuilder()
                .setId(subscription.getId())
                .setSender(subscription.getSenderId())
                .setRecipient(subscription.getRecipientId())
                .setAmount(subscription.getAmountNQT())
                .setFrequency(subscription.getFrequency())
                .setTimeNext(subscription.getTimeNext())
                .build();
    }

    public static BrsApi.Order buildOrder(Order order) {
        return BrsApi.Order.newBuilder()
                .setId(order.getId())
                .setAsset(order.getAssetId())
                .setAccount(order.getAccountId())
                .setQuantity(order.getQuantityQNT())
                .setPrice(order.getPriceNQT())
                .setHeight(order.getHeight())
                .setType(order.getProtobufType())
                .build();
    }

    public static BrsApi.DgsGood buildGoods(DigitalGoodsStore.Goods goods) {
        return BrsApi.DgsGood.newBuilder()
                .setId(goods.getId())
                .setSeller(goods.getSellerId())
                .setPrice(goods.getPriceNQT())
                .setQuantity(goods.getQuantity())
                .setIsDelisted(goods.isDelisted())
                .setTimestamp(goods.getTimestamp())
                .setName(goods.getName())
                .setDescription(goods.getDescription())
                .setTags(goods.getTags())
                .build();
    }

    public static BrsApi.EscrowTransaction buildEscrowTransaction(Escrow escrow) {
        return BrsApi.EscrowTransaction.newBuilder()
                .setEscrowId(escrow.getId())
                .setSender(escrow.getSenderId())
                .setRecipient(escrow.getRecipientId())
                .setAmount(escrow.getAmountNQT())
                .setRequiredSigners(escrow.getRequiredSigners())
                .setDeadline(escrow.getDeadline())
                .setDeadlineAction(Escrow.decisionToProtobuf(escrow.getDeadlineAction()))
                .build();
    }

    public static BrsApi.AssetTrade buildTrade(Trade trade, Asset asset) {
        return BrsApi.AssetTrade.newBuilder()
                .setAsset(trade.getAssetId())
                .setTradeType(trade.isBuy() ? BrsApi.AssetTradeType.BUY : BrsApi.AssetTradeType.SELL)
                .setSeller(trade.getSellerId())
                .setBuyer(trade.getBuyerId())
                .setPrice(trade.getPriceNQT())
                .setQuantity(trade.getQuantityQNT())
                .setAskOrder(trade.getAskOrderId())
                .setBidOrder(trade.getBidOrderId())
                .setAskOrderHeight(trade.getAskOrderHeight())
                .setBidOrderHeight(trade.getBidOrderHeight())
                .setBlock(trade.getBlockId())
                .setHeight(trade.getHeight())
                .setTimestamp(trade.getTimestamp())
                .setAssetName(asset.getName())
                .setAssetDescription(asset.getDescription())
                .build();
    }

    public static BrsApi.AssetTransfer buildTransfer(AssetTransfer assetTransfer, Asset asset) {
        return BrsApi.AssetTransfer.newBuilder()
                .setId(assetTransfer.getId())
                .setAsset(assetTransfer.getAssetId())
                .setSender(assetTransfer.getSenderId())
                .setRecipient(assetTransfer.getRecipientId())
                .setQuantity(assetTransfer.getQuantityQNT())
                .setHeight(assetTransfer.getHeight())
                .setTimestamp(assetTransfer.getTimestamp())
                .setAssetName(asset.getName())
                .setAssetDescription(asset.getDescription())
                .build();
    }

    public static BrsApi.DgsPurchase buildPurchase(DigitalGoodsStore.Purchase purchase, DigitalGoodsStore.Goods goods) {
        return BrsApi.DgsPurchase.newBuilder()
                .setId(purchase.getId())
                .setGood(purchase.getGoodsId())
                .setSeller(purchase.getSellerId())
                .setBuyer(purchase.getBuyerId())
                .setPrice(purchase.getPriceNQT())
                .setQuantity(purchase.getQuantity())
                .setTimestamp(purchase.getTimestamp())
                .setDeliveryDeadlineTimestamp(purchase.getDeliveryDeadlineTimestamp())
                .setGoodName(goods.getName())
                .setGoodDescription(goods.getDescription())
                .setNote(buildEncryptedData(purchase.getNote()))
                .setIsPending(purchase.isPending())
                .setDeliveredData(buildEncryptedData(purchase.getEncryptedGoods()))
                .setDeliveredDataIsText(purchase.goodsIsText())
                .addAllFeedback(purchase.getFeedbackNotes()
                        .stream()
                        .map(ProtoBuilder::buildEncryptedData)
                        .collect(Collectors.toList()))
                .addAllPublicFeedback(purchase.getPublicFeedback())
                .setRefundNote(buildEncryptedData(purchase.getRefundNote()))
                .setDiscount(purchase.getDiscountNQT())
                .setRefund(purchase.getRefundNQT())
                .build();
    }
}
