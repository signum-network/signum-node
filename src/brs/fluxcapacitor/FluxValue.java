package brs.fluxcapacitor;

import java.util.Arrays;
import java.util.List;

//TODO: Create JavaDocs and remove this
@SuppressWarnings({ "checkstyle:MissingJavadocTypeCheck", "checkstyle:MissingJavadocMethodCheck" })

public class FluxValue<T> {
    private T defaultValue;
    private List<ValueChange<T>> valueChanges;

    @SafeVarargs
    public FluxValue(T defaultValue, ValueChange<T>... valueChanges) {
        this.defaultValue = defaultValue;
        this.valueChanges = Arrays.asList(valueChanges);
    }

    public void updateValueChanges(List<ValueChange<T>> valueChanges) {
        this.valueChanges = valueChanges;
        this.defaultValue = valueChanges.get(0).getNewValue();
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public List<ValueChange<T>> getValueChanges() {
        return valueChanges;
    }

    public static class ValueChange<T> {
        private final HistoricalMoments historicalMoment;
        private final T newValue;

        public ValueChange(HistoricalMoments historicalMoment, T newValue) {
            this.historicalMoment = historicalMoment;
            this.newValue = newValue;
        }

        public HistoricalMoments getHistoricalMoment() {
            return historicalMoment;
        }

        public T getNewValue() {
            return newValue;
        }
    }
}
