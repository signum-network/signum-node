package brs.web.api.http.common;

import brs.props.PropertyService;
import brs.props.Props;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * The keys from {@code API.adminKeyList} that unlock the admin API. The example key that older
 * {@code node-default.properties} files ship is never accepted; comparisons take the same time
 * whichever key matches.
 */
public final class AdminKeys {

    /** The example value that older default configs carried; it is public, so it never unlocks anything. */
    public static final String EXAMPLE_KEY = "e673529588638d2129af1e0528a1642cf2e0c180";
    /** Shorter keys still work, but the node warns about them at startup. */
    public static final int MIN_RECOMMENDED_LENGTH = 32;

    /** How serious a weak {@code API.adminKeyList} entry is. */
    public enum Severity { ERROR, WARNING }

    /** A weak entry: what to log and at which level; the message never contains the key. */
    public record Weakness(Severity severity, String message) {
    }

    private final List<byte[]> keys;

    private AdminKeys(List<byte[]> keys) {
        this.keys = keys;
    }

    /** @return the configured admin keys without the example key */
    public static AdminKeys from(PropertyService propertyService) {
        List<byte[]> keys = new ArrayList<>();
        for (String key : propertyService.getStringList(Props.API_ADMIN_KEY_LIST)) {
            if (!isExampleKey(key)) {
                keys.add(key.getBytes(StandardCharsets.UTF_8));
            }
        }
        return new AdminKeys(keys);
    }

    /** @return true if {@code apiKey} is one of the admin keys; compares against every key */
    public boolean isAllowed(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }
        byte[] given = apiKey.getBytes(StandardCharsets.UTF_8);
        boolean allowed = false;
        for (byte[] key : keys) {
            allowed |= MessageDigest.isEqual(key, given);
        }
        return allowed;
    }

    public static boolean isExampleKey(String key) {
        return EXAMPLE_KEY.equals(key);
    }

    /**
     * @param configured the keys as configured, in order
     * @return one entry per weak key, naming its 1-based position but never the key
     */
    public static List<Weakness> describeWeaknesses(List<String> configured) {
        List<Weakness> weaknesses = new ArrayList<>();
        for (int i = 0; i < configured.size(); i++) {
            String key = configured.get(i);
            int entry = i + 1;
            if (isExampleKey(key)) {
                weaknesses.add(new Weakness(Severity.ERROR, "API.adminKeyList entry " + entry
                        + " is the example key from node-default.properties and is ignored; replace it"));
            } else if (key.length() < MIN_RECOMMENDED_LENGTH) {
                weaknesses.add(new Weakness(Severity.WARNING, "API.adminKeyList entry " + entry + " is shorter than "
                        + MIN_RECOMMENDED_LENGTH + " characters; use a long random key"));
            }
        }
        return weaknesses;
    }
}
