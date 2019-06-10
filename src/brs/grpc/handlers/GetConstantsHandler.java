package brs.grpc.handlers;

import brs.Constants;
import brs.Genesis;
import brs.TransactionType;
import brs.fluxcapacitor.FluxCapacitor;
import brs.fluxcapacitor.FluxValues;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import com.google.protobuf.Empty;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GetConstantsHandler implements GrpcApiHandler<Empty, BrsApi.Constants> {

    private final BrsApi.Constants constants;

    public GetConstantsHandler(FluxCapacitor fluxCapacitor) {
        List<BrsApi.Constants.TransactionType> transactionTypes = new ArrayList<>();
        TransactionType.getTransactionTypes()
                .forEach((key, value) -> transactionTypes
                        .add(BrsApi.Constants.TransactionType.newBuilder()
                                .setType(key)
                                .setDescription(TransactionType.getTypeDescription(key))
                                .addAllSubtypes(value.entrySet().stream()
                                        .map(entry -> BrsApi.Constants.TransactionType.TransactionSubtype.newBuilder()
                                                .setSubtype(entry.getKey())
                                                .setDescription(entry.getValue().getDescription())
                                                .build())
                                        .collect(Collectors.toList()))
                                .build()));

        this.constants =  BrsApi.Constants.newBuilder()
                .setGenesisBlock(Genesis.GENESIS_BLOCK_ID)
                .setGenesisAccount(Genesis.CREATOR_ID)
                .setMaxBlockPayloadLength(fluxCapacitor.getValue(FluxValues.MAX_PAYLOAD_LENGTH))
                .setMaxArbitraryMessageLength(Constants.MAX_ARBITRARY_MESSAGE_LENGTH)
                .addAllTransactionTypes(transactionTypes)
                .build();
    }

    @Override
    public BrsApi.Constants handleRequest(Empty empty) throws Exception {
        return constants;
    }
}
