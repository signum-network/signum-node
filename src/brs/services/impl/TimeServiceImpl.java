package brs.services.impl;

import brs.services.TimeService;
import brs.util.Time;
import brs.util.Time.FasterTime;

import java.util.concurrent.atomic.AtomicReference;

public class TimeServiceImpl implements TimeService {

  private static final AtomicReference<Time> time = new AtomicReference<>(new Time.EpochTime());

  @Override
  public int getEpochTime() {
    return time.get().getTime();
  }

  @Override
  public long getEpochTimeMillis() {
    return time.get().getTimeInMillis();
  }

  @Override
  public void setTime(FasterTime t) {
    time.set(t);
  }

}
