package brs.fluxcapacitor;

import brs.Constants;
import brs.Version;

//TODO: Create JavaDocs and remove this
@SuppressWarnings({
    "checkstyle:MissingJavadocTypeCheck",
    "checkstyle:MissingJavadocMethodCheck",
    "checkstyle:LineLengthCheck" })

public class FluxValues {
    private FluxValues() {
    }

    public static final FluxEnable REWARD_RECIPIENT_ENABLE = new FluxEnable(HistoricalMoments.REWARD_RECIPIENT_ENABLE);
    public static final FluxEnable DIGITAL_GOODS_STORE = new FluxEnable(HistoricalMoments.DIGITAL_GOODS_STORE_BLOCK);
    public static final FluxEnable AUTOMATED_TRANSACTION_BLOCK = new FluxEnable(
            HistoricalMoments.AUTOMATED_TRANSACTION_BLOCK);
    public static final FluxEnable AT_FIX_BLOCK_2 = new FluxEnable(HistoricalMoments.AT_FIX_BLOCK_2);
    public static final FluxEnable AT_FIX_BLOCK_3 = new FluxEnable(HistoricalMoments.AT_FIX_BLOCK_3);
    public static final FluxEnable AT_FIX_BLOCK_4 = new FluxEnable(HistoricalMoments.AT_FIX_BLOCK_4);
    public static final FluxEnable AT_FIX_BLOCK_5 = new FluxEnable(HistoricalMoments.AT_FIX_BLOCK_5);
    public static final FluxEnable PRE_POC2 = new FluxEnable(HistoricalMoments.PRE_POC2);
    public static final FluxEnable POC2 = new FluxEnable(HistoricalMoments.POC2);
    public static final FluxEnable SODIUM = new FluxEnable(HistoricalMoments.SODIUM);
    public static final FluxEnable SIGNUM = new FluxEnable(HistoricalMoments.SIGNUM);
    public static final FluxEnable POC_PLUS = new FluxEnable(HistoricalMoments.POC_PLUS);
    public static final FluxEnable SPEEDWAY = new FluxEnable(HistoricalMoments.SPEEDWAY);
    public static final FluxEnable SMART_TOKEN = new FluxEnable(HistoricalMoments.SMART_TOKEN);
    public static final FluxEnable SMART_FEES = new FluxEnable(HistoricalMoments.SMART_FEES);
    public static final FluxEnable SMART_ATS = new FluxEnable(HistoricalMoments.SMART_ATS);
    public static final FluxEnable DISTRIBUTION_FIX = new FluxEnable(HistoricalMoments.DISTRIBUTION_FIX);
    public static final FluxEnable PK_FREEZE = new FluxEnable(HistoricalMoments.PK_FREEZE);
    public static final FluxEnable PK_FREEZE2 = new FluxEnable(HistoricalMoments.PK_FREEZE2);
    public static final FluxEnable SMART_ALIASES = new FluxEnable(HistoricalMoments.SMART_ALIASES);
    public static final FluxEnable NEXT_FORK = new FluxEnable(HistoricalMoments.NEXT_FORK);

    public static final FluxValue<Integer> BLOCK_TIME = new FluxValue<>(240);
    public static final FluxValue<Long> FEE_QUANT = new FluxValue<>(
            Constants.ONE_SIGNA,
            new FluxValue.ValueChange<>(HistoricalMoments.PRE_POC2, Constants.FEE_QUANT_SIP3),
            new FluxValue.ValueChange<>(HistoricalMoments.SMART_FEES, Constants.FEE_QUANT_SIP34));
    public static final FluxValue<Short> AT_VERSION = new FluxValue<>((short) 1,
            new FluxValue.ValueChange<>(HistoricalMoments.SODIUM, (short) 2),
            new FluxValue.ValueChange<>(HistoricalMoments.SMART_ATS, (short) 3));

    public static final FluxValue<Integer> MAX_NUMBER_TRANSACTIONS = new FluxValue<>(
            255,
            new FluxValue.ValueChange<>(HistoricalMoments.PRE_POC2, 255 * 4),
            new FluxValue.ValueChange<>(HistoricalMoments.SMART_FEES, 255 * 4 * 2));
    public static final FluxValue<Integer> MAX_PAYLOAD_LENGTH = new FluxValue<>(
            255 * 176,
            new FluxValue.ValueChange<>(HistoricalMoments.PRE_POC2, 255 * 176 * 4),
            new FluxValue.ValueChange<>(HistoricalMoments.SMART_FEES, 255 * (176 + 8) * 4 * 2));

    public static final FluxValue<Long> MIN_CAPACITY = new FluxValue<>(
            1000L);
    public static final FluxValue<Integer> COMMITMENT_WAIT = new FluxValue<>(
            Constants.COMMITMENT_WAIT,
            new FluxValue.ValueChange<>(HistoricalMoments.SMART_ATS, Constants.MAX_ROLLBACK));
    public static final FluxValue<Long> AVERAGE_COMMITMENT_WINDOW = new FluxValue<>(
            24L,
            new FluxValue.ValueChange<>(HistoricalMoments.SPEEDWAY, 96L));

    public static final FluxValue<Version> MIN_PEER_VERSION = new FluxValue<>(
            Version.parse("3.6.0"),
            new FluxValue.ValueChange<>(HistoricalMoments.SMART_ALIASES, Version.parse("3.6.9")));
}
