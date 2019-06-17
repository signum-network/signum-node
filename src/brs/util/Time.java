package brs.util;

import brs.Constants;

// TODO burstkit4j integration
public interface Time {

  int getTime();

  long getTimeInMillis();

  final class EpochTime implements Time {

    public int getTime() {
      return (int)((System.currentTimeMillis() - Constants.EPOCH_BEGINNING + 500) / 1000);
    }

    public long getTimeInMillis() {
      return System.currentTimeMillis() - Constants.EPOCH_BEGINNING + 500;
    }
  }

  final class FasterTime implements Time {

    private final int multiplier;
    private final long systemStartTime;
    private final int time;

    public FasterTime(int time, int multiplier) {
      if (multiplier > 1000 || multiplier <= 0) {
        throw new IllegalArgumentException("Time multiplier must be between 1 and 1000");
      }
      this.multiplier = multiplier;
      this.time = time;
      this.systemStartTime = System.currentTimeMillis();
    }

    public int getTime() {
      return time + (int)((System.currentTimeMillis() - systemStartTime) / (1000 / multiplier));
    }

    public long getTimeInMillis() {
      return time + ((System.currentTimeMillis() - systemStartTime) / multiplier);
    }
  }
}
