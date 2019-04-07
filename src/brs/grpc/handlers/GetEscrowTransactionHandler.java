package brs.grpc.handlers;

import brs.Escrow;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.ApiException;
import brs.grpc.proto.BrsApi;
import brs.grpc.proto.ProtoBuilder;
import brs.services.EscrowService;

public class GetEscrowTransactionHandler implements GrpcApiHandler<BrsApi.GetByIdRequest, BrsApi.EscrowTransaction> {

    private final EscrowService escrowService;

    public GetEscrowTransactionHandler(EscrowService escrowService) {
        this.escrowService = escrowService;
    }

    @Override
    public BrsApi.EscrowTransaction handleRequest(BrsApi.GetByIdRequest request) throws Exception {
        long escrowId = request.getId();
        Escrow escrow = escrowService.getEscrowTransaction(escrowId);
        if (escrow == null) throw new ApiException("Could not find escrow");
        return ProtoBuilder.buildEscrowTransaction(escrow);
    }
}
