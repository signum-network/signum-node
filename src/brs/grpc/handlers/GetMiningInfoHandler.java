package brs.grpc.handlers;

import brs.Block;
import brs.Blockchain;
import brs.crypto.hash.Shabal256;
import brs.grpc.StreamResponseGrpcApiHandler;
import brs.grpc.proto.Brs;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class GetMiningInfoHandler implements StreamResponseGrpcApiHandler<Empty, Brs.MiningInfo> { // TODO block listener

    private final Blockchain blockchain;

    public GetMiningInfoHandler(Blockchain blockchain) {
        this.blockchain = blockchain;
    }

    @Override
    public void handleStreamRequest(Empty input, StreamObserver<Brs.MiningInfo> responseObserver) {
        Timer timer = new Timer();
        TimerTask sendMiningInfo = new TimerTask() {
            byte[] lastSentGenerationSignature;
            int lastSentHeight;
            @Override
            public void run() {
                try {
                    Block lastBlock = blockchain.getLastBlock();
                    int height = blockchain.getHeight() + 1;
                    byte[] generationSignature = calculateGenerationSignature(lastBlock);
                    long baseTarget = lastBlock.getBaseTarget();
                    if (height != lastSentHeight || !Arrays.equals(generationSignature, lastSentGenerationSignature)) {
                        Brs.MiningInfo newMiningInfo = Brs.MiningInfo.newBuilder()
                                .setHeight(height)
                                .setGenerationSignature(ByteString.copyFrom(generationSignature))
                                .setBaseTarget(baseTarget)
                                .build();
                        try {
                            responseObserver.onNext(newMiningInfo);
                        } catch (Exception e) {
                            timer.cancel();
                            return;
                        }
                        lastSentHeight = height;
                    }
                } catch (Exception e) {
                    responseObserver.onError(e);
                }
            }
        };
        timer.schedule(sendMiningInfo, 0, 1000);
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
