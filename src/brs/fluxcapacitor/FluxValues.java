package brs.fluxcapacitor;

import brs.Version;

public class FluxValues {
    private FluxValues() {
    }

    public static final FluxEnable REWARD_RECIPIENT_ENABLE = new FluxEnable(HistoricalMoments.REWARD_RECIPIENT_ENABLE);
    public static final FluxEnable DIGITAL_GOODS_STORE = new FluxEnable(HistoricalMoments.DIGITAL_GOODS_STORE_BLOCK);
    public static final FluxEnable AUTOMATED_TRANSACTION_BLOCK = new FluxEnable(HistoricalMoments.AUTOMATED_TRANSACTION_BLOCK);
    public static final FluxEnable AT_FIX_BLOCK_2 = new FluxEnable(HistoricalMoments.AT_FIX_BLOCK_2);
    public static final FluxEnable AT_FIX_BLOCK_3 = new FluxEnable(HistoricalMoments.AT_FIX_BLOCK_3);
    public static final FluxEnable AT_FIX_BLOCK_4 = new FluxEnable(HistoricalMoments.AT_FIX_BLOCK_4);
    public static final FluxEnable PRE_POC2 = new FluxEnable(HistoricalMoments.PRE_POC2);
    public static final FluxEnable POC2 = new FluxEnable(HistoricalMoments.POC2);
    public static final FluxEnable SODIUM = new FluxEnable(HistoricalMoments.SODIUM);
    public static final FluxEnable PRE_POC_PLUS = new FluxEnable(HistoricalMoments.PRE_POC_PLUS);
    public static final FluxEnable POC_PLUS = new FluxEnable(HistoricalMoments.POC_PLUS);
    public static final FluxEnable NEXT_FORK = new FluxEnable(HistoricalMoments.NEXT_FORK);

    public static final FluxValue<Short> AT_VERSION = new FluxValue<>((short) 1, 
                                                                      new FluxValue.ValueChange<>(HistoricalMoments.SODIUM, (short) 2), 
                                                                      new FluxValue.ValueChange<>(HistoricalMoments.AT_SUPPORT_ASSETS_ENABLE, (short) 3));

    public static final FluxValue<Integer> MAX_NUMBER_TRANSACTIONS = new FluxValue<>(255, new FluxValue.ValueChange<>(HistoricalMoments.PRE_POC2, 1020));
    public static final FluxValue<Integer> MAX_PAYLOAD_LENGTH = new FluxValue<>(255 * 176, new FluxValue.ValueChange<>(HistoricalMoments.PRE_POC2, 1020 * 176));
    
    public static final FluxValue<Version> MIN_PEER_VERSION = new FluxValue<>(Version.parse("2.5.0"),
        new FluxValue.ValueChange<>(HistoricalMoments.PRE_POC_PLUS, Version.parse("2.9.9"))
        );
}
