package brs.fluxcapacitor;

import brs.Blockchain;
import brs.props.PropertyService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default Signum implementation of a {@link FluxCapacitor}.
 */
public class FluxCapacitorImpl implements FluxCapacitor {

    private final PropertyService propertyService;
    private final Blockchain blockchain;

    // Map used as a cache.
    private final Map<HistoricalMoments, Integer> momentsCache = new ConcurrentHashMap<>();

    /**
     * Creates a new FluxCapacitor with this implementation.
     * 
     * @param blockchain the active {@link Blockchain}
     * @param propertyService the active {@link PropertyService}
     */
    public FluxCapacitorImpl(Blockchain blockchain, PropertyService propertyService) {
        this.propertyService = propertyService;
        this.blockchain = blockchain;
    }

    /**
     * Gets the value of a specified {@link FluxValue} at the blockchain's current
     * height.
     * 
     * @param <T>       the type in the FluxValue
     * @param fluxValue the FluxValue
     * @return the value contained in the FluxValue at the blockchain's current
     *         height
     */
    @Override
    public <T> T getValue(FluxValue<T> fluxValue) {
        return getValueAt(fluxValue, blockchain.getHeight());
    }

    /**
     * Gets the value of a specified {@link FluxValue} at a specified height.
     * 
     * @param <T>       the type in the FluxValue
     * @param fluxValue the FluxValue
     * @return the value contained in the FluxValue at the specified height
     */
    @Override
    public <T> T getValue(FluxValue<T> fluxValue, int height) {
        return getValueAt(fluxValue, height);
    }

    /**
     * Gets the height of the given {@link HistoricalMoments} instance.
     * @param historicalMoment the historical moment
     * @return an int representing the height
     */
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

    /**
     * Gets the value from a {@link FluxValue} that is active at the specified height.
     * @param <T> the type of the contained value
     * @param fluxValue the FluxValue to get the value from
     * @param height the height to get the active value 
     * @return a {@link T} that is the value at the height specified
     */
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

    /**
     * Returns the blockchain height at which this {@link FluxEnable} takes effect.
     * 
     * @param fluxEnable the FluxEnable to query
     * @return an Integer representing the height
     */
    @Override
    public Integer getStartingHeight(FluxEnable fluxEnable) {
        return getHistoricalMomentHeight(fluxEnable.getEnablePoint());
    }
}
