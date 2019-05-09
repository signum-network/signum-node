package brs.fluxcapacitor;

import brs.Blockchain;
import brs.props.PropertyService;
import brs.props.Props;

import java.util.HashMap;
import java.util.Map;

public class FluxCapacitorImpl implements FluxCapacitor {

    private final PropertyService propertyService;
    private final Blockchain blockchain;

    // Map of Flux Value -> Change Height -> Index of ValueChange in FluxValue. Used as a cache.
    private final Map<FluxValue<?>, Map<Integer, Integer>> valueChangesPerFluxValue = new HashMap<>();

    public FluxCapacitorImpl(Blockchain blockchain, PropertyService propertyService) {
        this.propertyService = propertyService;
        this.blockchain = blockchain;
    }

    @Override
    public <T> T getValue(FluxValue<T> fluxValue) {
        return getValueAt(fluxValue, blockchain.getHeight());
    }

    @Override
    public <T> T getValue(FluxValue<T> fluxValue, int height) {
        return getValueAt(fluxValue, height);
    }

    private int getHistoricalMomentHeight(HistoricalMoments historicalMoment) {
        if (propertyService.getBoolean(Props.DEV_TESTNET)) {
            int overridingHeight = propertyService.getInt(historicalMoment.getOverridingProperty());
            return overridingHeight >= 0 ? overridingHeight : historicalMoment.getTestnetHeight();
        } else {
            return historicalMoment.getMainnetHeight();
        }
    }

    private <T> Map<Integer, Integer> computeValuesAtHeights(FluxValue<T> fluxValue) {
        return valueChangesPerFluxValue.computeIfAbsent(fluxValue, fv -> {
            Map<Integer, Integer> valueChangeIndexAtHeight = new HashMap<>();
            FluxValue.ValueChange<T>[] valueChanges = fluxValue.getValueChanges();
            for (int i = 0; i < valueChanges.length; i++) {
                valueChangeIndexAtHeight.put(getHistoricalMomentHeight(valueChanges[i].getHistoricalMoment()), i);
            }
            return valueChangeIndexAtHeight;
        });
    }

    private <T> T getValueAt(FluxValue<T> fluxValue, int height) {
        T mostRecentValue = fluxValue.getDefaultValue();
        int mostRecentChangeHeight = 0;
        for (Map.Entry<Integer, Integer> entry : computeValuesAtHeights(fluxValue).entrySet()) {
            int entryHeight = entry.getKey();
            if (entryHeight <= height && entryHeight >= mostRecentChangeHeight) {
                mostRecentValue = fluxValue.getValueChanges()[entry.getValue()].getNewValue();
                mostRecentChangeHeight = entryHeight;
            }
        }
        return mostRecentValue;
    }

    @Override
    public Integer getStartingHeight(FluxEnable fluxEnable) {
        return getHistoricalMomentHeight(fluxEnable.getEnablePoint());
    }
}
