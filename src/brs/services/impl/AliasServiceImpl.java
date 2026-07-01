package brs.services.impl;

import static brs.schema.Tables.ALIAS;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;

import org.jooq.SelectJoinStep;

import brs.Account;
import brs.Alias;
import brs.Alias.Offer;
import brs.Attachment;
import brs.Signum;
import brs.Subscription;
import brs.Transaction;
import brs.TransactionType;
import brs.db.SignumKey;
import brs.db.VersionedEntityTable;
import brs.db.sql.Db;
import brs.db.store.AliasStore;
import brs.fluxcapacitor.FluxValues;
import brs.props.Props;
import brs.services.AliasService;
import brs.services.SubscriptionService;
import brs.util.CollectionWithIndex;
import signumj.crypto.SignumCrypto;

public class AliasServiceImpl implements AliasService {

    private final AliasStore aliasStore;
    private final VersionedEntityTable<Alias> aliasTable;
    private final SignumKey.LongKeyFactory<Alias> aliasDbKeyFactory;
    private final VersionedEntityTable<Offer> offerTable;
    private final SignumKey.LongKeyFactory<Offer> offerDbKeyFactory;

    private static final String MAIN_TLD = "signum";
    private static final String[] DEFAULT_TLDS = {
            "blockchain", "coin", "crypto", "dao", "decentral", "dex", "free", "nft", "p2p", "sig", "signa", "sns",
            "w3", "wallet", "web3", "x", "y", "z"
    };

    public AliasServiceImpl(AliasStore aliasStore) {
        this.aliasStore = aliasStore;
        this.aliasTable = aliasStore.getAliasTable();
        this.aliasDbKeyFactory = aliasStore.getAliasDbKeyFactory();
        this.offerTable = aliasStore.getOfferTable();
        this.offerDbKeyFactory = aliasStore.getOfferDbKeyFactory();
    }

    public void addDefaultTLDs() {
        try {
            Signum.getStores().beginTransaction();

            // TODO: should be removed prior to the next release
            // try {
            // Statement selectTx = Db.getConnection().createStatement();
            // selectTx.executeUpdate(
            // "update alias set latest=1 where alias_name like 'signum' and height=0;" +
            // "delete from alias where alias_name like 'signum' and height <>0 and tld is
            // null;"
            // );
            // }
            // catch (Exception e) {
            // e.printStackTrace();
            // }
            // // TODO: end of DB patch, to be removed

            if (aliasStore.getTLD(MAIN_TLD) == null) {
                Attachment.MessagingTldAssignment attachment = new Attachment.MessagingTldAssignment(MAIN_TLD, 0);
                addTLD(0L, null, attachment);
            }
            for (String tldName : DEFAULT_TLDS) {
                Alias tld = aliasStore.getTLD(tldName);
                if (tld != null) {
                    continue;
                }

                SignumCrypto crypto = SignumCrypto.getInstance();
                long id = crypto.hashToId(crypto.getSha256().digest(tldName.getBytes(StandardCharsets.UTF_8)))
                        .getSignedLongId();
                Attachment.MessagingTldAssignment attachment = new Attachment.MessagingTldAssignment(tldName, 0);
                addTLD(id, null, attachment);
            }
            Signum.getStores().commitTransaction();
        } finally {
            Signum.getStores().endTransaction();
        }
    }

    public Alias getAlias(String aliasName, long tld) {
        return aliasStore.getAlias(aliasName, tld);
    }

    public Alias getAlias(long id) {
        return aliasTable.get(aliasDbKeyFactory.newKey(id));
    }

    public Alias getTLD(String tldName) {
        return aliasStore.getTLD(tldName);
    }

    public Alias getTLD(Long tldId) {
        return tldId == null ? null : aliasStore.getTLD(tldId);
    }

    @Override
    public Offer getOffer(Alias alias) {
        return offerTable.get(offerDbKeyFactory.newKey(alias.getId()));
    }

    @Override
    public int getAliasCount() {
        return aliasTable.getCount();
    }

    @Override
    public int getAliasCount(long tld) {
        return Db.fetchWithDSLContext(ctx -> {
            SelectJoinStep<?> r = ctx.selectCount().from(ALIAS);
            return (r.where(ALIAS.LATEST.isTrue()).and(ALIAS.TLD.eq(tld))).fetchOne(0, int.class);
        });
    }

    @Override
    public CollectionWithIndex<Alias> getAliasesByOwner(long accountId, String name, Long tld, int from, int to) {
        return new CollectionWithIndex<Alias>(aliasStore.getAliasesByOwner(accountId, name, tld, from, to), from, to);
    }

    @Override
    public CollectionWithIndex<Alias> getTLDs(int from, int to) {
        return new CollectionWithIndex<Alias>(aliasStore.getTLDs(from, to), from, to);
    }

    @Override
    public Collection<Alias> getTLDs(long accountId) {
        return aliasStore.getAliasesByOwner(accountId, null, null, 0, -1);
    }

    @Override
    public CollectionWithIndex<Alias.Offer> getAliasOffers(long account, long buyer, int from, int to) {
        return new CollectionWithIndex<Alias.Offer>(aliasStore.getAliasOffers(account, buyer, from, to), from, to);
    }

    private void createSubscription(Alias alias, int timestamp, boolean updateSubscription) {
        if (!Signum.getFluxCapacitor().getValue(FluxValues.SMART_ALIASES)) {
            return;
        }

        SubscriptionService subscriptionService = Signum.getSubscriptionService();
        int frequency = Signum.getPropertyService().getInt(Props.ALIAS_RENEWAL_FREQUENCY);
        long fee = Signum.getFluxCapacitor().getValue(FluxValues.FEE_QUANT)
                * TransactionType.BASELINE_ALIAS_RENEWAL_FACTOR;
        Subscription subscription = subscriptionService.getSubscription(alias.getId());
        if (subscription != null && updateSubscription && subscription.getSenderId() != alias.getAccountId()) {
            subscription.setSenderId(alias.getAccountId());
            ArrayList<Subscription> subscriptions = new ArrayList<>();
            subscriptions.add(subscription);
            Signum.getStores().getSubscriptionStore().saveSubscriptions(subscriptions);
        }
        if (subscription == null) {
            subscriptionService.addSubscription(Account.getAccount(alias.getAccountId()), alias.getId(), alias.getId(),
                    fee, timestamp, frequency);
        }
    }

    @Override
    public void addOrUpdateAlias(Transaction transaction, Attachment.MessagingAliasAssignment attachment) {
        Alias alias = getAlias(attachment.getAliasName(), attachment.getTld());
        if (alias == null) {
            SignumKey aliasDBId = aliasDbKeyFactory.newKey(transaction.getId());
            alias = new Alias(transaction.getId(), aliasDBId, transaction, attachment);
        } else {
            alias.setAccountId(transaction.getSenderId());
            alias.setAliasUri(attachment.getAliasUri());
            alias.setTimestamp(transaction.getBlockTimestamp());
        }
        aliasTable.insert(alias);

        createSubscription(alias, transaction.getBlockTimestamp(), true);
    }

    @Override
    public void addTLD(long id, Transaction transaction, Attachment.MessagingTldAssignment attachment) {
        SignumKey aliasDBId = aliasDbKeyFactory.newKey(id);
        Alias alias = new Alias(id, aliasDBId, transaction, attachment);
        aliasTable.insert(alias);
    }

    @Override
    public void sellAlias(Transaction transaction, Attachment.MessagingAliasSell attachment) {
        final long priceNQT = attachment.getPriceNqt();
        final long buyerId = transaction.getRecipientId();
        Alias alias = attachment.getVersion() > 1 ? getAlias(attachment.getAliasId())
                : getAlias(attachment.getAliasName(), 0L);
        if (priceNQT > 0) {
            Offer offer = getOffer(alias);
            if (offer == null) {
                SignumKey dbKey = offerDbKeyFactory.newKey(alias.getId());
                offerTable.insert(new Offer(dbKey, alias.getId(), priceNQT, buyerId));
            } else {
                offer.setPriceNqt(priceNQT);
                offer.setBuyerId(buyerId);
                offerTable.insert(offer);
            }
        } else {
            changeOwner(buyerId, alias, transaction.getBlockTimestamp(), false);
        }
    }

    @Override
    public void changeOwner(long newOwnerId, Alias alias, int timestamp, boolean updateSubscription) {
        alias.setAccountId(newOwnerId);
        alias.setTimestamp(timestamp);
        aliasTable.insert(alias);

        final Offer offer = getOffer(alias);
        offerTable.delete(offer);

        if (alias.getTld() != null) {
            // only create the subscription if this is not a TLD (that has a null TLD)
            createSubscription(alias, timestamp, updateSubscription);
        }
    }
}
