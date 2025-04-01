package brs.fluxcapacitor;

/**
 * An interface defining a class that handles values that change over the
 * history of the Signum blockchain.
 */
public interface FluxCapacitor {
    /**
     * Gets the value of a specified {@link FluxValue} at the blockchain's current
     * height.
     * 
     * @param <T>       the type in the FluxValue
     * @param fluxValue the FluxValue
     * @return the value contained in the FluxValue at the blockchain's current
     *         height
     */
    <T> T getValue(FluxValue<T> fluxValue);

    /**
     * Gets the value of a specified {@link FluxValue} at a specified height.
     * 
     * @param <T>       the type in the FluxValue
     * @param fluxValue the FluxValue
     * @return the value contained in the FluxValue at the specified height
     */
    <T> T getValue(FluxValue<T> fluxValue, int height);

    /**
     * Returns the blockchain height at which this {@link FluxEnable} takes effect.
     * 
     * @param fluxEnable the FluxEnable to query
     * @return an Integer representing the height
     */
    Integer getStartingHeight(FluxEnable fluxEnable);
}
