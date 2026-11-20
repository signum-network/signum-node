package brs.peer;

import java.math.BigDecimal;
import java.util.OptionalLong;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Measures the local clock against a peer's from one request: the peer reports its clock in the
 * reply, and the midpoint of the local send and receive times is taken as the moment it did, which
 * cancels a symmetric network delay.
 */
final class PeerClock {

    /** Reply field with the peer's clock, in epoch milliseconds. */
    static final String FIELD = "epochTimeMillis";

    private PeerClock() {
    }

    /**
     * @param sentAtMillis     local epoch millis right before the request was sent
     * @param receivedAtMillis local epoch millis right after the reply was read
     * @param peerTime         the peer's {@link #FIELD} value, may be null or malformed
     * @return the local clock minus the peer's clock in milliseconds (positive: local clock ahead),
     *         saturated at the long range; empty if the peer sent no usable time
     */
    static OptionalLong offsetMillis(long sentAtMillis, long receivedAtMillis, JsonElement peerTime) {
        if (receivedAtMillis < sentAtMillis || !(peerTime instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            return OptionalLong.empty();
        }
        long peerMillis;
        try {
            peerMillis = new BigDecimal(primitive.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
        long midpoint = (sentAtMillis >> 1) + (receivedAtMillis >> 1) + (sentAtMillis & receivedAtMillis & 1);
        return OptionalLong.of(saturatedSubtract(midpoint, peerMillis));
    }

    /**
     * The host part of a peer address ({@code host}, {@code host:port}, {@code [ipv6]:port}), so one
     * host on many ports counts as one peer.
     */
    static String hostOf(String peerAddress) {
        if (peerAddress.startsWith("[")) {
            int end = peerAddress.indexOf(']');
            return end < 0 ? peerAddress : peerAddress.substring(0, end + 1);
        }
        int colon = peerAddress.indexOf(':');
        // more than one colon: a bare IPv6 address without a port
        if (colon < 0 || peerAddress.indexOf(':', colon + 1) >= 0) {
            return peerAddress;
        }
        return peerAddress.substring(0, colon);
    }

    /**
     * Rounds to whole seconds what this node tells others about its clock: enough for a drift
     * warning, and less detail for fingerprinting a node by its clock skew.
     */
    static long roundToSecond(long millis) {
        return Math.floorDiv(millis + 500, 1000) * 1000;
    }

    private static long saturatedSubtract(long a, long b) {
        long difference = a - b;
        // overflow if a and b have different signs and the result's sign differs from a's
        if (((a ^ b) & (a ^ difference)) < 0) {
            return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return difference;
    }
}
