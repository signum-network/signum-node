package brs.services;

import brs.Account;
import brs.Block;
import brs.Subscription;

import java.util.Collection;

public interface SubscriptionService {

  Subscription getSubscription(Long id);

  Collection<Subscription> getSubscriptionsByParticipant(Long accountId);

  Collection<Subscription> getSubscriptionsToId(Long accountId);

  void addSubscription(Account sender, Account recipient, Long id, Long amountNQT, int startTimestamp, int frequency);

  boolean isEnabled();

  void applyConfirmed(Block block, int blockchainHeight);

  void removeSubscription(Long id);

  long calculateFees(int timestamp);

  void clearRemovals();

  void addRemoval(Long id);

  long applyUnconfirmed(int timestamp);
}
