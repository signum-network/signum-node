package brs.fluxcapacitor;

public interface FluxCapacitor {

  boolean isActive(FeatureToggle featureToggle);

  boolean isActive(FeatureToggle featureToggle, int height);

  Integer getInt(FluxInt fluxInt);

  Integer getInt(FluxInt fluxInt, int height);

  Integer getStartingHeight(FeatureToggle featureToggle);

}
