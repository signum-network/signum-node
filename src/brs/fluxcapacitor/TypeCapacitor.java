package brs.fluxcapacitor;

import brs.fluxcapacitor.FluxHistory.Element;

abstract class TypeCapacitor<T> {

  private final HistorianImpl historian;

  TypeCapacitor(HistorianImpl historian) {
    this.historian = historian;
  }

  public T getValueAtHeight(FluxHistory<T> flux, int height) {
    for (int i = flux.getHistory().size() - 1; i >= 0; i--) {
      final Element<T> historicalElement = flux.getHistory().get(i);

      if (historian.hasHappened(historicalElement.getMoment(), height)) {
        return historicalElement.getValue();
      }
    }
    return flux.getDefaultValue();
  }

  public Integer getStartingHeight(FluxHistory<T> flux) {
    int lowestStartingHeight = -1;
    for (Element<T> historicalElement : flux.getHistory()) {
      int startingHeight = historian.getStartingHeight(historicalElement.getMoment());
      if (lowestStartingHeight == -1 || startingHeight < lowestStartingHeight) {
        lowestStartingHeight = startingHeight;
      }
    }
    return lowestStartingHeight;
  }
}
