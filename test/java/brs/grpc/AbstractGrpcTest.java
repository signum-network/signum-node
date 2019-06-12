package brs.grpc;

import brs.*;
import brs.assetexchange.AssetExchange;
import brs.feesuggestions.FeeSuggestionCalculator;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxCapacitorImpl;
import brs.grpc.proto.BrsApiServiceGrpc;
import brs.grpc.proto.BrsService;
import brs.props.PropertyService;
import brs.props.Props;
import brs.services.*;
import io.grpc.Context;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Rule;
import org.mockito.ArgumentMatchers;

import java.io.IOException;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public abstract class AbstractGrpcTest {

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    protected BrsApiServiceGrpc.BrsApiServiceBlockingStub brsService;

    protected void defaultBrsService() throws IOException {
        // Mocks
        Block latestBlock = mock(Block.class);
        BlockchainProcessor blockchainProcessor = mock(BlockchainProcessor.class);
        Blockchain blockchain = mock(Blockchain.class);
        BlockService blockService = mock(BlockService.class);
        AccountService accountService = mock(AccountService.class);
        Generator generator = mock(Generator.class);
        TransactionProcessor transactionProcessor = mock(TransactionProcessor.class);
        TimeService timeService = mock(TimeService.class);
        FeeSuggestionCalculator feeSuggestionCalculator = mock(FeeSuggestionCalculator.class);
        ATService atService = mock(ATService.class);
        AliasService aliasService = mock(AliasService.class);
        IndirectIncomingService indirectIncomingService = mock(IndirectIncomingService.class);
        EscrowService escrowService = mock(EscrowService.class);
        AssetExchange assetExchange = mock(AssetExchange.class);
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        DGSGoodsStoreService dgsGoodsStoreService = mock(DGSGoodsStoreService.class);
        PropertyService propertyService = mock(PropertyService.class);

        // Returns
        doReturn(Integer.MAX_VALUE).when(blockchain).getHeight();
        doReturn(latestBlock).when(blockchain).getLastBlock();
        doReturn(true).when(propertyService).getBoolean(Props.DEV_TESTNET);
        doReturn(new byte[32]).when(generator).calculateGenerationSignature(ArgumentMatchers.any(), ArgumentMatchers.anyLong());
        doReturn(0L).when(latestBlock).getGeneratorId();
        doReturn(new byte[32]).when(latestBlock).getGenerationSignature();

        // Real classes
        FluxCapacitor fluxCapacitor = new FluxCapacitorImpl(blockchain, propertyService);
        TransactionType.init(blockchain, fluxCapacitor, accountService, dgsGoodsStoreService, aliasService, assetExchange, subscriptionService, escrowService);

        setUpBrsService(new BrsService(blockchainProcessor, blockchain, blockService, accountService, generator, transactionProcessor, timeService, feeSuggestionCalculator, atService, aliasService, indirectIncomingService, fluxCapacitor, escrowService, assetExchange, subscriptionService, dgsGoodsStoreService, propertyService));
    }

    protected void setUpBrsService(BrsService brsService) throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        grpcCleanup.register(InProcessServerBuilder.forName(serverName).directExecutor().addService(brsService).build().start());

        this.brsService = BrsApiServiceGrpc.newBlockingStub(grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
    }

    /**
     * Needed so that streaming calls can be gracefully shutdown afterwards.
     * @param runnable The test to execute
     */
    protected void runAndCancel(Runnable runnable) {
        Context.CancellableContext withCancellation = Context.current().withCancellation();
        Context prevCtx = withCancellation.attach();
        try {
            runnable.run();
        } finally {
            withCancellation.detach(prevCtx);
            withCancellation.close();
        }
    }
}
