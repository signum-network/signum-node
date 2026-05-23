package brs.services.impl;

import brs.*;
import brs.Alias.Offer;
import brs.SignumException.NotValidException;
import brs.db.SignumKey;
import brs.db.SignumKey.LongKeyFactory;
import brs.db.TransactionDb;
import brs.db.VersionedEntityTable;
import brs.db.store.SubscriptionStore;
import brs.fluxcapacitor.FluxValues;
import brs.services.AccountService;
import brs.services.AliasService;
import brs.services.SubscriptionService;
import brs.util.Convert;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscriptionServiceImpl implements SubscriptionService {

    private final Logger logger = LoggerFactory.getLogger(SubscriptionServiceImpl.class);
    private final SubscriptionStore subscriptionStore;
    private final VersionedEntityTable<Subscription> subscriptionTable;
    private final LongKeyFactory<Subscription> subscriptionDbKeyFactory;

    private final Blockchain blockchain;
    private final AliasService aliasService;
    private final AccountService accountService;

    private final TransactionDb transactionDb;

    private static final List<Transaction> paymentTransactions = new ArrayList<>();
    private static final List<Subscription> appliedSubscriptions = new ArrayList<>();
    private static final Set<Long> removeSubscriptions = new HashSet<>();

    public SubscriptionServiceImpl(SubscriptionStore subscriptionStore, TransactionDb transactionDb,
            Blockchain blockchain, AliasService aliasService, AccountService accountService) {
        this.subscriptionStore = subscriptionStore;
        this.subscriptionTable = subscriptionStore.getSubscriptionTable();
        this.subscriptionDbKeyFactory = subscriptionStore.getSubscriptionDbKeyFactory();
        this.transactionDb = transactionDb;
        this.blockchain = blockchain;
        this.aliasService = aliasService;
        this.accountService = accountService;
    }

    @Override
    public Subscription getSubscription(Long id) {
        return subscriptionTable.get(subscriptionDbKeyFactory.newKey(id));
    }

    @Override
    public Collection<Subscription> getSubscriptionsByParticipant(Long accountId) {
        return subscriptionStore.getSubscriptionsByParticipant(accountId);
    }

    @Override
    public Collection<Subscription> getSubscriptionsToId(Long accountId) {
        return subscriptionStore.getSubscriptionsToId(accountId);
    }

    @Override
    public void addSubscription(Account sender, long recipientId, Long id, Long amountNQT, int startTimestamp,
            int frequency) {
        final SignumKey dbKey = subscriptionDbKeyFactory.newKey(id);
        final Subscription subscription = new Subscription(sender.getId(), recipientId, id, amountNQT, frequency,
                startTimestamp + frequency, dbKey);

        subscriptionTable.insert(subscription);
    }

    @Override
    public boolean isEnabled() {
        if (blockchain.getLastBlock().getHeight() >= Constants.SIGNUM_SUBSCRIPTION_START_BLOCK) {
            return true;
        }

        final Alias subscriptionEnabled = aliasService.getAlias("featuresubscription", 0L);
        return subscriptionEnabled != null && subscriptionEnabled.getAliasUri().equals("enabled");
    }

    @Override
    public void applyConfirmed(Block block, int blockchainHeight) {
        paymentTransactions.clear();
        for (Subscription subscription : appliedSubscriptions) {
            apply(block, blockchainHeight, subscription);
        }
        subscriptionStore.saveSubscriptions(appliedSubscriptions);

        if (!paymentTransactions.isEmpty()) {
            transactionDb.saveTransactions(paymentTransactions);
            block.setSubscriptionTransactions(paymentTransactions);
        }
        removeSubscriptions.forEach(this::removeSubscription);
        if (logger.isDebugEnabled()) {
            if (appliedSubscriptions.size() > 0 || removeSubscriptions.size() > 0) {
                logger.debug("Subscriptions: applied {}, removed {}", appliedSubscriptions.size(),
                        removeSubscriptions.size());
            }
        }
    }

    private long getFee(int height) {
        if (Signum.getFluxCapacitor().getValue(FluxValues.SODIUM, height))
            return Signum.getFluxCapacitor().getValue(FluxValues.FEE_QUANT, height);
        return Constants.ONE_SIGNA;
    }

    @Override
    public void removeSubscription(Long id) {
        Subscription subscription = subscriptionTable.get(subscriptionDbKeyFactory.newKey(id));
        if (subscription != null) {
            if (subscription.getRecipientId() != 0L) {
                Alias alias = aliasService.getAlias(subscription.getRecipientId());
                if (alias != null && alias.getId() == subscription.getId()) {
                    Offer offer = aliasService.getOffer(alias);
                    if (offer != null) {
                        Signum.getStores().getAliasStore().getOfferTable().delete(offer);
                    }
                    Signum.getStores().getAliasStore().getAliasTable().delete(alias);
                }
            }
            subscriptionTable.delete(subscription);
        }
    }

    @Override
    public long calculateFees(int timestamp, int height) {
        long totalFeeNQT = 0;
        List<Subscription> appliedUnconfirmedSubscriptions = new ArrayList<>();
        for (Subscription subscription : subscriptionStore.getUpdateSubscriptions(timestamp)) {
            if (removeSubscriptions.contains(subscription.getId())) {
                continue;
            }
            if (applyUnconfirmed(subscription, height)) {
                appliedUnconfirmedSubscriptions.add(subscription);
            }
        }
        if (!appliedUnconfirmedSubscriptions.isEmpty()) {
            for (Subscription subscription : appliedUnconfirmedSubscriptions) {
                totalFeeNQT = Convert.safeAdd(totalFeeNQT, getFee(height));
                undoUnconfirmed(subscription, height);
            }
        }
        return totalFeeNQT;
    }

    @Override
    public void clearRemovals() {
        removeSubscriptions.clear();
    }

    @Override
    public void addRemoval(Long id) {
        removeSubscriptions.add(id);
    }

    @Override
    public long applyUnconfirmed(int timestamp, int height) {
        appliedSubscriptions.clear();
        long totalFees = 0;
        for (Subscription subscription : subscriptionStore.getUpdateSubscriptions(timestamp)) {
            if (removeSubscriptions.contains(subscription.getId())) {
                continue;
            }
            if (applyUnconfirmed(subscription, height)) {
                appliedSubscriptions.add(subscription);
                totalFees = Convert.safeAdd(totalFees, getFee(height));
            } else {
                removeSubscriptions.add(subscription.getId());
            }
        }
        return totalFees;
    }

    private Account getSender(Subscription subscription) {
        return accountService.getAccount(subscription.getSenderId());
    }

    private Account getRecipient(Subscription subscription) {
        if (Signum.getFluxCapacitor().getValue(FluxValues.SMART_ALIASES)) {
            Alias alias = aliasService.getAlias(subscription.getRecipientId());
            if (alias != null) {
                Alias tld = aliasService.getTLD(alias.getTld());
                return accountService.getOrAddAccount(tld.getAccountId());
            }
        }
        return accountService.getOrAddAccount(subscription.getRecipientId());
    }

    private boolean applyUnconfirmed(Subscription subscription, int height) {
        Account sender = getSender(subscription);
        long totalAmountNQT = Convert.safeAdd(subscription.getAmountNQT(), getFee(height));

        Account.Balance senderBalance = sender == null ? null : Account.getAccountBalance(sender.getId());
        if (sender == null || senderBalance.getUnconfirmedBalanceNqt() < totalAmountNQT) {
            return false;
        }

        accountService.addToUnconfirmedBalanceNQT(sender, -totalAmountNQT);

        return true;
    }

    private void undoUnconfirmed(Subscription subscription, int height) {
        Account sender = getSender(subscription);
        long totalAmountNQT = Convert.safeAdd(subscription.getAmountNQT(), getFee(height));

        if (sender != null) {
            accountService.addToUnconfirmedBalanceNQT(sender, totalAmountNQT);
        }
    }

    private void apply(Block block, int blockchainHeight, Subscription subscription) {
        Account sender = getSender(subscription);
        Account recipient = getRecipient(subscription);

        long totalAmountNQT = Convert.safeAdd(subscription.getAmountNQT(), getFee(block.getHeight()));

        accountService.addToBalanceNQT(sender, -totalAmountNQT);
        accountService.addToBalanceAndUnconfirmedBalanceNQT(recipient, subscription.getAmountNQT());

        Attachment.AbstractAttachment attachment = new Attachment.AdvancedPaymentSubscriptionPayment(
                subscription.getId(), blockchainHeight);
        Transaction.Builder builder = new Transaction.Builder((byte) 1,
                sender.getPublicKey(), subscription.getAmountNQT(),
                getFee(block.getHeight()),
                subscription.getTimeNext(), (short) 1440, attachment);

        try {
            builder.senderId(sender.getId())
                    .recipientId(recipient.getId())
                    .blockId(block.getId())
                    .height(block.getHeight())
                    .blockTimestamp(block.getTimestamp())
                    .ecBlockHeight(0)
                    .ecBlockId(0L);
            Transaction transaction = builder.build();
            if (!transactionDb.hasTransaction(transaction.getId())) {
                paymentTransactions.add(transaction);
            }
        } catch (NotValidException e) {
            throw new RuntimeException("Failed to build subscription payment transaction", e);
        }

        subscription.timeNextGetAndAdd(subscription.getFrequency());
    }

}
