package brs.grpc.handlers;

import brs.Block;
import brs.Blockchain;
import brs.BlockchainProcessor;
import brs.Generator;
import brs.grpc.StreamResponseGrpcApiHandler;
import brs.grpc.proto.BrsApi;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class GetMiningInfoHandler implements StreamResponseGrpcApiHandler<Empty, BrsApi.MiningInfo> {

    /**
     * Listener should close connection if it receives null.
     */
    private final Set<Consumer<BrsApi.MiningInfo>> listeners = new HashSet<>();
    private final Generator generator;
    private final AtomicReference<BrsApi.MiningInfo> currentMiningInfo = new AtomicReference<>();

    public GetMiningInfoHandler(BlockchainProcessor blockchainProcessor, Blockchain blockchain, Generator generator) {
        this.generator = generator;
        blockchainProcessor.addListener(this::onBlock, BlockchainProcessor.Event.BLOCK_PUSHED);
        onBlock(blockchain.getLastBlock());
    }

    private void onBlock(Block block) {
        synchronized (currentMiningInfo) {
            byte[] nextGenSig = generator.calculateGenerationSignature(block.getGenerationSignature(), block.getGeneratorId());
            BrsApi.MiningInfo miningInfo = currentMiningInfo.get();
            if (miningInfo == null || !Arrays.equals(miningInfo.getGenerationSignature().toByteArray(), nextGenSig) || miningInfo.getHeight() - 1 != block.getHeight() || miningInfo.getBaseTarget() != block.getBaseTarget()) {
                currentMiningInfo.set(BrsApi.MiningInfo.newBuilder()
                        .setGenerationSignature(ByteString.copyFrom(nextGenSig))
                        .setHeight(block.getHeight() + 1)
                        .setBaseTarget(block.getBaseTarget())
                        .build());
                notifyListeners(currentMiningInfo.get());
            }
        }
    }

    private void notifyListeners(BrsApi.MiningInfo miningInfo) {
        synchronized (listeners) {
            listeners.removeIf(listener -> {
                try {
                    listener.accept(miningInfo);
                    return false;
                } catch (Exception e) {
                    try {
                        listener.accept(null);
                    } catch (Exception ignored) {
                        // Ignore any errors attempting to disconnect as we may already be disconnected
                    }
                    return true;
                }
            });
        }
    }

    private void addListener(Consumer<BrsApi.MiningInfo> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    @Override
    public void handleStreamRequest(Empty input, StreamObserver<BrsApi.MiningInfo> responseObserver) {
        responseObserver.onNext(currentMiningInfo.get());
        addListener(miningInfo -> {
            if (miningInfo == null) {
                responseObserver.onCompleted();
            } else {
                responseObserver.onNext(miningInfo);
            }
        });
    }
}
