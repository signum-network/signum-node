package brs.db.sql;

import brs.db.PeerDb;
import brs.schema.tables.records.PeerRecord;
import org.jooq.Insert;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static brs.schema.Tables.PEER;

public class SqlPeerDb implements PeerDb {

    @Override public List<String> loadPeers() {
        return Db.useDSLContext(ctx -> {
            return ctx.selectFrom(PEER).fetch(PEER.ADDRESS, String.class);
        });
    }

    @Override public void deletePeers(Collection<String> peers) {
        Db.useDSLContext(ctx -> {
            for (String peer : peers) {
                ctx.deleteFrom(PEER).where(PEER.ADDRESS.eq(peer)).execute();
            }
        });
    }

    @Override public void addPeers(Collection<String> peers) {
        Db.useDSLContext(ctx -> {
            List<Insert<PeerRecord>> inserts = peers.stream().map(peer -> ctx.insertInto(PEER).set(PEER.ADDRESS, peer)).collect(Collectors.toList());
            ctx.batch(inserts).execute();
        });
    }

    @Override
    public void optimize() {
        Db.optimizeTable(PEER.getName());
    }
}
