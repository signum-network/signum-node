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
import java.util.function.Consumer;

public class GetMiningInfoHandler implements StreamResponseGrpcApiHandler<Empty, Brs.MiningInfo> {

    /**
     * Listener should close connection if it receives null.
     */
    private final Set<Consumer<Brs.MiningInfo>> listeners = new HashSet<>();

    private final Object updateLastLock = new Object();

    private byte[] lastGenerationSignature;
    private int lastHeight = 0;
    private long lastBaseTarget = 0;

    public GetMiningInfoHandler(BlockchainProcessor blockchainProcessor) {
        blockchainProcessor.addListener(this::onBlock, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);
    }

    private void onBlock(Block block) {
        synchronized (updateLastLock) {
            byte[] nextGenSig = calculateGenerationSignature(block);
            if (!Arrays.equals(lastGenerationSignature, nextGenSig) || lastHeight != block.getHeight() || lastBaseTarget != block.getBaseTarget()) {
                lastGenerationSignature = nextGenSig;
                lastHeight = block.getHeight();
                lastBaseTarget = block.getBaseTarget();
                notifyListeners(Brs.MiningInfo.newBuilder()
                        .setGenerationSignature(ByteString.copyFrom(lastGenerationSignature))
                        .setHeight(lastHeight)
                        .setBaseTarget(lastBaseTarget)
                        .build());
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
