package brs.grpc.handlers;

import brs.feesuggestions.FeeSuggestion;
import brs.feesuggestions.FeeSuggestionCalculator;
import brs.grpc.GrpcApiHandler;
import brs.grpc.proto.BrsApi;
import com.google.protobuf.Empty;

public class SuggestFeeHandler implements GrpcApiHandler<Empty, BrsApi.FeeSuggestion> {

    private final FeeSuggestionCalculator feeSuggestionCalculator;

    public SuggestFeeHandler(FeeSuggestionCalculator feeSuggestionCalculator) {
        this.feeSuggestionCalculator = feeSuggestionCalculator;
    }

    @Override
    public BrsApi.FeeSuggestion handleRequest(Empty empty) throws Exception {
        FeeSuggestion feeSuggestion = feeSuggestionCalculator.giveFeeSuggestion();
        return BrsApi.FeeSuggestion.newBuilder()
                .setCheap(feeSuggestion.getCheapFee())
                .setStandard(feeSuggestion.getStandardFee())
                .setPriority(feeSuggestion.getPriorityFee())
                .build();
    }
}
