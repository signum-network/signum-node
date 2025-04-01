package brs.fluxcapacitor;

import java.util.Arrays;
import java.util.List;

/**
 * A value that can change at specified {@link HistoricalMoments}.
 * 
 * @param <T> the type contained in the FluxValue.
 */
public class FluxValue<T> {
    private T defaultValue;
    private List<ValueChange<T>> valueChanges;

    /**
     * Instantiate a new FluxValue of type T.
     * 
     * @param defaultValue the default value of this FluxValue
     * @param valueChanges optional list of {@link ValueChange}s
     *                     specifying any changes at specified
     *                     {@link HistoricalMoments}
     */
    @SafeVarargs
    public FluxValue(T defaultValue, ValueChange<T>... valueChanges) {
        this.defaultValue = defaultValue;
        this.valueChanges = Arrays.asList(valueChanges);
    }

    /**
     * Updates the list of {@link ValueChange}s in this FluxValue.
     * 
     * @param valueChanges the list of ValueChanges
     */
    public void updateValueChanges(List<ValueChange<T>> valueChanges) {
        this.valueChanges = valueChanges;
        this.defaultValue = valueChanges.get(0).getNewValue();
    }

    /**
     * Gets the default value of this FluxValue before any changes take effect.
     * 
     * @return the default value as type {@link T}
     */
    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * Gets the list of {@link ValueChange}s in the FluxValue.
     * 
     * @return a list of ValueChanges
     */
    public List<ValueChange<T>> getValueChanges() {
        return valueChanges;
    }

    /**
     * Represents a change to a {@link FluxValue} at a specifc
     * {@link HistoricalMoments}.
     * 
     * @param <T> the type contained in the FluxValue
     */
    public static class ValueChange<T> {
        private final HistoricalMoments historicalMoment;
        private final T newValue;

        /**
         * Instantiate a ValueChange.
         * 
         * @param historicalMoment The historical moment at which this value takes
         *                         effect
         * @param newValue         the new value
         */
        public ValueChange(HistoricalMoments historicalMoment, T newValue) {
            this.historicalMoment = historicalMoment;
            this.newValue = newValue;
        }

        /**
         * Get the historical moment at which this ValueChange happens.
         * 
         * @return a {@link HistoricalMoments}
         */
        public HistoricalMoments getHistoricalMoment() {
            return historicalMoment;
        }

        /**
         * Get the value this ValueChange represents.
         * 
         * @return a type {@link T}
         */
        public T getNewValue() {
            return newValue;
        }
    }
}
