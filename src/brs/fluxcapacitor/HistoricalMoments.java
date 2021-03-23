package brs.fluxcapacitor;

import brs.props.Prop;
import brs.props.Props;

public enum HistoricalMoments {
    REWARD_RECIPIENT_ENABLE(6500, 6500, Props.DEV_REWARD_RECIPIENT_ENABLE_BLOCK_HEIGHT),
    DIGITAL_GOODS_STORE_BLOCK(11800, 0, Props.DEV_DIGITAL_GOODS_STORE_BLOCK_HEIGHT),
    AUTOMATED_TRANSACTION_BLOCK(49200, 0, Props.DEV_AUTOMATED_TRANSACTION_BLOCK_HEIGHT),
    AT_FIX_BLOCK_2(67000, 0, Props.DEV_AT_FIX_BLOCK_2_BLOCK_HEIGHT),
    AT_FIX_BLOCK_3(92000, 0, Props.DEV_AT_FIX_BLOCK_3_BLOCK_HEIGHT),
    AT_FIX_BLOCK_4(255000, 0, Props.DEV_AT_FIX_BLOCK_4_BLOCK_HEIGHT),
    PRE_POC2(500000, 0, Props.DEV_PRE_POC2_BLOCK_HEIGHT),
    POC2(502000, 0, Props.DEV_POC2_BLOCK_HEIGHT),
    SODIUM(765_000, 160_620, Props.DEV_SODIUM_BLOCK_HEIGHT),
    PRE_POC_PLUS(Integer.MAX_VALUE, 269_100, Props.DEV_PRE_POC_PLUS),
    POC_PLUS(Integer.MAX_VALUE, 269_700, Props.DEV_POC_PLUS),
    NEXT_FORK(Integer.MAX_VALUE, Integer.MAX_VALUE, Props.DEV_NEXT_FORK_BLOCK_HEIGHT),
    AT_SUPPORT_ASSETS_ENABLE(Integer.MAX_VALUE, Integer.MAX_VALUE, Props.DEV_AT_SUPPORT_ASSETS_ENABLE_BLOCK_HEIGHT);
    

    private final int mainnetHeight;
    private final int testnetHeight;
    private final Prop<Integer> overridingProperty;

    HistoricalMoments(int mainnetHeight, int testnetHeight, Prop<Integer> overridingProperty) {
        this.mainnetHeight = mainnetHeight;
        this.testnetHeight = testnetHeight;
        this.overridingProperty = overridingProperty;
    }

    public int getMainnetHeight() {
        return mainnetHeight;
    }

    public int getTestnetHeight() {
        return testnetHeight;
    }

    public Prop<Integer> getOverridingProperty() {
        return overridingProperty;
    }
}
