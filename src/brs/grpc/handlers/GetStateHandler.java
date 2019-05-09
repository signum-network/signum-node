package brs.grpc.handlers;

import brs.*;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import brs.peer.Peer;
import brs.peer.Peers;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.TimeService;
import com.google.protobuf.Empty;

public class GetStateHandler implements GrpcApiHandler<Empty, BrsApi.State> {

    private final TimeService timeService;
    private final Blockchain blockchain;
    private final Generator generator;
    private final BlockchainProcessor blockchainProcessor;
    private final PropertyService propertyService;

    public GetStateHandler(TimeService timeService, Blockchain blockchain, Generator generator, BlockchainProcessor blockchainProcessor, PropertyService propertyService) {
        this.timeService = timeService;
        this.blockchain = blockchain;
        this.generator = generator;
        this.blockchainProcessor = blockchainProcessor;
        this.propertyService = propertyService;
    }

    @Override
    public BrsApi.State handleRequest(Empty empty) throws Exception {
        Block lastBlock = blockchain.getLastBlock();
        Peer lastBlockchainFeeder = blockchainProcessor.getLastBlockchainFeeder();
        return BrsApi.State.newBuilder()
                .setApplication(Burst.APPLICATION)
                .setVersion(Burst.VERSION.toString())
                .setTime(BrsApi.Time.newBuilder().setTime(timeService.getEpochTime()).build())
                .setLastBlock(lastBlock.getId())
                .setLastHeight(blockchain.getHeight())
                .setCumulativeDifficulty(lastBlock.getCumulativeDifficulty().toString())
                .setNumberOfPeers(Peers.getAllPeers().size())
                .setNumberOfActivePeers(Peers.getActivePeers().size())
                .setNumberOfForgers(generator.getAllGenerators().size())
                .setLastBlockchainFeeder(lastBlockchainFeeder == null ? "null" : lastBlockchainFeeder.getAnnouncedAddress())
                .setLastBlockchainFeederHeight(blockchainProcessor.getLastBlockchainFeederHeight())
                .setIsScanning(blockchainProcessor.isScanning())
                .setAvailableProcessors(Runtime.getRuntime().availableProcessors())
                .setMaxMemory(Runtime.getRuntime().maxMemory())
                .setTotalMemory(Runtime.getRuntime().totalMemory())
                .setFreeMemory(Runtime.getRuntime().freeMemory())
                .setIndirectIncomingServiceEnabled(propertyService.getBoolean(Props.INDIRECT_INCOMING_SERVICE_ENABLE))
                .build();
    }
}
