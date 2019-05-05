package brs;

import brs.db.BurstKey;

import java.util.concurrent.atomic.AtomicInteger;

public class Subscription {

  public final Long senderId;
  public final Long recipientId;
  public final Long id;
  public final BurstKey dbKey;
  public final Long amountNQT;
  public final int frequency;
  private final AtomicInteger timeNext;

  public Subscription(Long senderId,
                         Long recipientId,
                         Long id,
                         Long amountNQT,
                         int frequency,
                         int timeNext,
                         BurstKey dbKey
                         ) {
    this.senderId = senderId;
    this.recipientId = recipientId;
    this.id = id;
    this.dbKey = dbKey;
    this.amountNQT = amountNQT;
    this.frequency  = frequency;
    this.timeNext = new AtomicInteger(timeNext);
  }

  public Long getSenderId() {
    return senderId;
  }

  public Long getAmountNQT() {
    return amountNQT;
  }

  public Long getRecipientId() {
    return recipientId;
  }

  public Long getId() {
    return id;
  }

  public int getFrequency() {
    return frequency;
  }

  public int getTimeNext() {
    return timeNext.get();
  }

  public void timeNextGetAndAdd(int frequency) {
    timeNext.getAndAdd(frequency);
  }
}
