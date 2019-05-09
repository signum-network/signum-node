package brs.fluxcapacitor;

/**
 * A special type of FluxValue used for eg. forks that goes from disabled to enabled at a certain historical moment.
 */
public class FluxEnable extends FluxValue<Boolean> {
    private final HistoricalMoments enablePoint;

    public FluxEnable(HistoricalMoments enablePoint) {
        super(false, new ValueChange<>(enablePoint, true));
        this.enablePoint = enablePoint;
    }

    public HistoricalMoments getEnablePoint() {
        return enablePoint;
    }
}
