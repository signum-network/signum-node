package brs;

import brs.peer.Peer;
import brs.util.JSON;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Looks among the connected peers for one whose chain has a higher cumulative difficulty than ours.
 * <p>
 * The search polls at most a given number of peers and stops as soon as it may not go on, for
 * example when the download thread is paused for a pop-off or the node shuts down. Without the
 * limit it looked until a better peer showed up, holding the download lock: when every connected
 * peer with a better chain is blacklisted, for example because its chain is invalid, none ever
 * shows up, and the node neither downloads nor pops off nor shuts down.
 */
final class BetterPeerSearch {

    /** A peer with a better chain, with the height and cumulative difficulty it reported. */
    record Feeder(Peer peer, int height, BigInteger cumulativeDifficulty) {
    }

    /** Is told about every peer that reported its height, better chain or not. */
    interface FeederListener {
        void reportedHeight(Peer peer, int height);
    }

    private static final Logger logger = LoggerFactory.getLogger(BetterPeerSearch.class);

    private final Supplier<Peer> connectedPeers;
    private final Predicate<Peer> usable;
    private final JsonElement request;
    private final FeederListener feederListener;
    private final BooleanSupplier mayContinue;
    private final int maxPolls;

    /**
     * @param connectedPeers any connected peer, {@code null} if there is none
     * @param usable         whether a peer may be asked, for example by version and network
     * @param request        the prepared {@code getCumulativeDifficulty} request
     * @param feederListener told about every peer that reported its height
     * @param mayContinue    false once the search must stop, checked before every poll
     * @param maxPolls       the most polls in one search, at least 1. Every poll draws a random
     *                       connected peer, so one search may ask a peer twice and skip another
     */
    BetterPeerSearch(Supplier<Peer> connectedPeers, Predicate<Peer> usable, JsonElement request,
            FeederListener feederListener, BooleanSupplier mayContinue, int maxPolls) {
        this.connectedPeers = connectedPeers;
        this.usable = usable;
        this.request = request;
        this.feederListener = feederListener;
        this.mayContinue = mayContinue;
        // at least one poll, also for a misconfigured limit: none would keep the node from syncing
        this.maxPolls = Math.max(1, maxPolls);
    }

    /**
     * @return a peer whose cumulative difficulty is higher than {@code ours}, or {@code null} if
     *         none was found within the polls or the search had to stop
     */
    Feeder find(BigInteger ours) {
        for (int poll = 0; poll < maxPolls; poll++) {
            if (!mayContinue.getAsBoolean()) {
                return null;
            }
            Peer peer = connectedPeers.get();
            if (peer == null) {
                logger.debug("No peer connected.");
                return null;
            }
            Feeder feeder = ask(peer);
            if (feeder != null && feeder.cumulativeDifficulty().compareTo(ours) > 0) {
                return feeder;
            }
        }
        logger.debug("No peer with a higher cumulative difficulty than {} in {} polls", ours, maxPolls);
        return null;
    }

    private Feeder ask(Peer peer) {
        if (!usable.test(peer)) {
            // ignore this peer, it will be removed by the peers discovery thread
            return null;
        }
        JsonObject response = peer.send(request);
        if (response == null) {
            return null;
        }
        if (response.get("blockchainHeight") == null) {
            logger.debug("Peer {} has no chainheight", peer.getAnnouncedAddress());
            return null;
        }
        int height = JSON.getAsInt(response.get("blockchainHeight"));
        feederListener.reportedHeight(peer, height);
        String cumulativeDifficulty = JSON.getAsString(response.get("cumulativeDifficulty"));
        if (cumulativeDifficulty == null) {
            logger.debug("Peer CumulativeDifficulty is null");
            return null;
        }
        return new Feeder(peer, height, new BigInteger(cumulativeDifficulty));
    }
}
