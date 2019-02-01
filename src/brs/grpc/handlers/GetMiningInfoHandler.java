package brs.grpc.handlers;

import brs.Block;
import brs.BlockchainProcessor;
import brs.crypto.hash.Shabal256;
import brs.grpc.StreamResponseGrpcApiHandler;
import brs.grpc.proto.Brs;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class GetMiningInfoHandler implements StreamResponseGrpcApiHandler<Empty, Brs.MiningInfo> {

    /**
     * Listener should close connection if it receives null.
     */
    private final Set<Consumer<Brs.MiningInfo>> listeners = new HashSet<>();

    private final AtomicReference<Brs.MiningInfo> currentMiningInfo = new AtomicReference<>();

    public GetMiningInfoHandler(BlockchainProcessor blockchainProcessor) {
        blockchainProcessor.addListener(this::onBlock, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);
    }

    private void onBlock(Block block) {
        synchronized (currentMiningInfo) {
            byte[] nextGenSig = calculateGenerationSignature(block);
            Brs.MiningInfo miningInfo = currentMiningInfo.get();
            if (miningInfo == null || !Arrays.equals(miningInfo.getGenerationSignature().toByteArray(), nextGenSig) || miningInfo.getHeight() - 1 != block.getHeight() || miningInfo.getBaseTarget() != block.getBaseTarget()) {
                currentMiningInfo.set(Brs.MiningInfo.newBuilder()
                        .setGenerationSignature(ByteString.copyFrom(nextGenSig))
                        .setHeight(block.getHeight() + 1)
                        .setBaseTarget(block.getBaseTarget())
                        .build());
                notifyListeners(currentMiningInfo.get());
            }
        }
    }

    private void notifyListeners(Brs.MiningInfo miningInfo) {
        synchronized (listeners) {
            listeners.removeIf(listener -> {
                try {
                    listener.accept(miningInfo);
                    return false;
                } catch (Throwable e) {
                    try {
                        listener.accept(null);
                    } catch (Throwable ignored) {
                    }
                    return true;
                }
            });
        }
    }

    private void addListener(Consumer<Brs.MiningInfo> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    @Override
    public void handleStreamRequest(Empty input, StreamObserver<Brs.MiningInfo> responseObserver) {
        responseObserver.onNext(currentMiningInfo.get());
        addListener(miningInfo -> {
            if (miningInfo == null) {
                responseObserver.onCompleted();
            } else {
                responseObserver.onNext(miningInfo);
            }
        });
    }

    private byte[] calculateGenerationSignature(Block previousBlock) {
        byte[] lastGenSig = previousBlock.getGenerationSignature();
        long lastGenerator = previousBlock.getGeneratorId();

        ByteBuffer buf = ByteBuffer.allocate(32 + 8);
        buf.put(lastGenSig);
        buf.putLong(lastGenerator);

        Shabal256 md = new Shabal256();
        md.update(buf.array());
        return md.digest();
    }
}
