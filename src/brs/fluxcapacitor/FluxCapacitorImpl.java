package brs.fluxcapacitor;

import brs.Blockchain;
import brs.props.PropertyService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

//TODO: Create JavaDocs and remove this
@SuppressWarnings({ "checkstyle:MissingJavadocTypeCheck", "checkstyle:MissingJavadocMethodCheck" })

public class FluxCapacitorImpl implements FluxCapacitor {

    private final PropertyService propertyService;
    private final Blockchain blockchain;

    // Map used as a cache.
    private final Map<HistoricalMoments, Integer> momentsCache = new ConcurrentHashMap<>();

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
        Integer cacheHeight = momentsCache.get(historicalMoment);
        if (cacheHeight != null) {
            return cacheHeight;
        }
        int overridingHeight = historicalMoment.getOverridingProperty() == null ? -1
                : propertyService.getInt(historicalMoment.getOverridingProperty());
        int height = overridingHeight >= 0 ? overridingHeight : historicalMoment.getMainnetHeight();
        momentsCache.put(historicalMoment, height);

        return height;
    }

    private <T> T getValueAt(FluxValue<T> fluxValue, int height) {
        T mostRecentValue = fluxValue.getDefaultValue();
        int mostRecentChangeHeight = 0;
        for (FluxValue.ValueChange<T> valueChange : fluxValue.getValueChanges()) {
            int entryHeight = getHistoricalMomentHeight(valueChange.getHistoricalMoment());
            if (entryHeight <= height && entryHeight >= mostRecentChangeHeight) {
                mostRecentValue = valueChange.getNewValue();
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
