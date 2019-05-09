package brs.fluxcapacitor;

public interface FluxCapacitor {
  <T> T getValue(FluxValue<T> fluxValue);
  <T> T getValue(FluxValue<T> fluxValue, int height);
  Integer getStartingHeight(FluxEnable fluxEnable);
}
