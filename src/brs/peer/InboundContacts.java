package brs.peer;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Remembers when another node last sent this node a P2P request, separately for public and local
 * source addresses, so the web UI can tell whether the node is reachable from outside. In memory only.
 */
public final class InboundContacts {

    private static final String OCTET = "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final Pattern IPV4 = Pattern.compile("^" + OCTET + "(\\." + OCTET + "){3}$");

    private final AtomicLong lastPublic = new AtomicLong();
    private final AtomicLong lastLocal = new AtomicLong();

    /**
     * @param remoteAddress the request's source address (an IP literal); loopback and anything that
     *                      is not an IP literal are ignored, so no name lookup ever happens here
     * @param nowMillis     the time of the request
     */
    public void record(String remoteAddress, long nowMillis) {
        InetAddress address = parseLiteral(remoteAddress);
        if (address == null || address.isLoopbackAddress() || address.isAnyLocalAddress()) {
            return;
        }
        AtomicLong target = isLocal(address) ? lastLocal : lastPublic;
        target.accumulateAndGet(nowMillis, Math::max);
    }

    public OptionalLong lastPublicMillis() {
        return toOptional(lastPublic.get());
    }

    public OptionalLong lastLocalMillis() {
        return toOptional(lastLocal.get());
    }

    private static OptionalLong toOptional(long millis) {
        return millis == 0 ? OptionalLong.empty() : OptionalLong.of(millis);
    }

    private static InetAddress parseLiteral(String remoteAddress) {
        if (remoteAddress == null) {
            return null;
        }
        // only a valid dotted IPv4 or a string with ':' (IPv6; host names never contain one) reaches
        // getByName, which parses literals without a name lookup
        if (!IPV4.matcher(remoteAddress).matches() && remoteAddress.indexOf(':') < 0) {
            return null;
        }
        try {
            return InetAddress.getByName(remoteAddress);
        } catch (UnknownHostException | SecurityException e) {
            return null;
        }
    }

    private static boolean isLocal(InetAddress address) {
        if (address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address) {
            return (bytes[0] & 0xfe) == 0xfc; // unique local fc00::/7
        }
        return (bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 0x40; // carrier-grade NAT 100.64.0.0/10
    }
}
