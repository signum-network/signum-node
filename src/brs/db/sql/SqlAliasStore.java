package brs.db.sql;

import brs.Alias;
import brs.Signum;
import brs.db.SignumKey;
import brs.db.VersionedEntityTable;
import brs.db.store.AliasStore;
import brs.db.store.DerivedTableManager;
import brs.util.Convert;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SortField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static brs.schema.Tables.ALIAS;
import static brs.schema.Tables.ALIAS_OFFER;

public class SqlAliasStore implements AliasStore {

    private static final DbKey.LongKeyFactory<Alias.Offer> offerDbKeyFactory = new DbKey.LongKeyFactory<Alias.Offer>(
            ALIAS_OFFER.ID) {
        @Override
        public SignumKey newKey(Alias.Offer offer) {
            return offer.dbKey;
        }
    };

    public SqlAliasStore(DerivedTableManager derivedTableManager) {
        offerTable = new VersionedEntitySqlTable<Alias.Offer>("alias_offer", ALIAS_OFFER, offerDbKeyFactory,
                derivedTableManager) {
            @Override
            protected Alias.Offer load(DSLContext ctx, Record record) {
                return new SqlOffer(record);
            }

            @Override
            protected void save(DSLContext ctx, Alias.Offer offer) {
                saveOffer(offer);
            }
        };

        aliasTable = new VersionedEntitySqlTable<Alias>("alias", brs.schema.Tables.ALIAS, aliasDbKeyFactory,
                derivedTableManager) {
            @Override
            protected Alias load(DSLContext ctx, Record record) {
                return new SqlAlias(record);
            }

            @Override
            protected void save(DSLContext ctx, Alias alias) {
                saveAlias(ctx, alias);
            }

            @Override
            protected List<SortField<?>> defaultSort() {
                List<SortField<?>> sort = new ArrayList<>();
                sort.add(tableClass.field("alias_name_lower", String.class).asc());
                return sort;
            }
        };
    }

    @Override
    public SignumKey.LongKeyFactory<Alias.Offer> getOfferDbKeyFactory() {
        return offerDbKeyFactory;
    }

    private static final SignumKey.LongKeyFactory<Alias> aliasDbKeyFactory = new DbKey.LongKeyFactory<Alias>(ALIAS.ID) {

        @Override
        public SignumKey newKey(Alias alias) {
            return alias.dbKey;
        }
    };

    @Override
    public SignumKey.LongKeyFactory<Alias> getAliasDbKeyFactory() {
        return aliasDbKeyFactory;
    }

    @Override
    public VersionedEntityTable<Alias> getAliasTable() {
        return aliasTable;
    }

    private class SqlOffer extends Alias.Offer {
        private SqlOffer(Record record) {
            super(record.get(ALIAS_OFFER.ID), record.get(ALIAS_OFFER.PRICE),
                    Convert.nullToZero(record.get(ALIAS_OFFER.BUYER_ID)),
                    offerDbKeyFactory.newKey(record.get(ALIAS_OFFER.ID)));
        }
    }

    private void saveOffer(Alias.Offer offer) {
        Db.useDSLContext(ctx -> {
            ctx.insertInto(ALIAS_OFFER, ALIAS_OFFER.ID, ALIAS_OFFER.PRICE, ALIAS_OFFER.BUYER_ID, ALIAS_OFFER.HEIGHT)
                    .values(offer.getId(), offer.getPriceNqt(), (offer.getBuyerId() == 0 ? null : offer.getBuyerId()),
                            Signum.getBlockchain().getHeight())
                    .execute();
        });
    }

    private final VersionedEntityTable<Alias.Offer> offerTable;

    @Override
    public VersionedEntityTable<Alias.Offer> getOfferTable() {
        return offerTable;
    }

    private class SqlAlias extends Alias {
        private SqlAlias(Record record) {
            super(
                    record.get(ALIAS.ID),
                    record.get(ALIAS.ACCOUNT_ID),
                    record.get(ALIAS.ALIAS_NAME),
                    record.get(ALIAS.TLD),
                    record.get(ALIAS.ALIAS_URI),
                    record.get(ALIAS.TIMESTAMP),
                    aliasDbKeyFactory.newKey(record.get(ALIAS.ID)));
        }
    }

    private void saveAlias(DSLContext ctx, Alias alias) {
        ctx.insertInto(ALIAS).set(ALIAS.ID, alias.getId()).set(ALIAS.ACCOUNT_ID, alias.getAccountId())
                .set(ALIAS.ALIAS_NAME, alias.getAliasName()).set(ALIAS.TLD, alias.getTld())
                .set(ALIAS.ALIAS_NAME_LOWER, alias.getAliasName().toLowerCase(Locale.ENGLISH))
                .set(ALIAS.ALIAS_URI, alias.getAliasUri()).set(ALIAS.TIMESTAMP, alias.getTimestamp())
                .set(ALIAS.HEIGHT, Signum.getBlockchain().getHeight()).execute();
    }

    private final VersionedEntityTable<Alias> aliasTable;

    @Override
    public Collection<Alias> getAliasesByOwner(long accountId, String name, Long tld, int from, int to) {
        Condition condition = ALIAS.TLD.isNotNull();
        if (tld != null) {
            condition = ALIAS.TLD.eq(tld);
        }
        if (accountId != 0L) {
            condition = condition.and(ALIAS.ACCOUNT_ID.eq(accountId));
        }
        if (name != null) {
            condition = condition.and(ALIAS.ALIAS_NAME_LOWER.like(name.toLowerCase()));
        }
        return aliasTable.getManyBy(condition, from, to);
    }

    @Override
    public Collection<Alias> getTLDs(int from, int to) {
        return aliasTable.getManyBy(brs.schema.Tables.ALIAS.TLD.isNull(), from, to);
    }

    @Override
    public Collection<Alias.Offer> getAliasOffers(long account, long buyer, int from, int to) {
        Condition conditions = ALIAS_OFFER.LATEST.eq(true);
        if (account != 0L) {
            Result<Record1<Long>> myAliases = Db.fetchWithDSLContext(ctx -> {
                return ctx.select(ALIAS.ID).from(ALIAS).where(ALIAS.ACCOUNT_ID.eq(account)).fetch();
            });
            conditions = conditions.and(ALIAS_OFFER.ID.in(myAliases));
        }
        if (buyer != 0L) {
            conditions = conditions.and(ALIAS_OFFER.BUYER_ID.eq(buyer));
        }
        return offerTable.getManyBy(conditions, from, to);
    }

    @Override
    public Alias getTLD(String tldName) {
        return aliasTable.getBy(brs.schema.Tables.ALIAS.ALIAS_NAME_LOWER.eq(tldName.toLowerCase(Locale.ENGLISH))
                .and(ALIAS.TLD.isNull()));
    }

    @Override
    public Alias getTLD(long tldId) {
        return aliasTable.getBy(brs.schema.Tables.ALIAS.ID.eq(tldId).and(ALIAS.TLD.isNull()));
    }

    @Override
    public Alias getAlias(String aliasName, long tld) {
        return aliasTable.getBy(brs.schema.Tables.ALIAS.ALIAS_NAME_LOWER.eq(aliasName.toLowerCase(Locale.ENGLISH))
                .and(ALIAS.TLD.eq(tld)));
    }

}
