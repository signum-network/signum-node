package brs.fluxcapacitor;

/**
 * A special type of FluxValue used for eg. forks that goes from disabled
 * to enabled at a certain historical moment.
 */
public class FluxEnable extends FluxValue<Boolean> {
    private final HistoricalMoments enablePoint;

    /**
     * Create a FluxEnable value.
     * 
     * @param enablePoint The historical moment at which this FluxEnable takes
     *                    effect
     */
    public FluxEnable(HistoricalMoments enablePoint) {
        super(false, new ValueChange<>(enablePoint, true));
        this.enablePoint = enablePoint;
    }

    /**
     * Returns the moment this FluxEnable takes effect.
     * 
     * @return an {@link HistoricalMoments}
     */
    public HistoricalMoments getEnablePoint() {
        return enablePoint;
    }
}
