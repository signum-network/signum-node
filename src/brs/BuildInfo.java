package brs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides build-time information injected into the application.
 *
 * <p>The version is the single source of truth from {@code gradle.properties},
 * written into the {@code /version.properties} classpath resource at build time
 * by the {@code generateVersionProperties} Gradle task. When the resource is
 * absent (e.g. running from an IDE without the generated resource), a
 * development fallback is used so the node is flagged as a prerelease.
 */
public final class BuildInfo {

    static final String RESOURCE = "/version.properties";
    static final String DEV_FALLBACK = "0.0.0-dev";

    private BuildInfo() {
    }

    /** Loads the version from the classpath resource, falling back to a dev version. */
    public static Version loadVersion() {
        try (InputStream in = BuildInfo.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Version.parse(DEV_FALLBACK);
            }
            Properties props = new Properties();
            props.load(in);
            return fromProperties(props);
        } catch (IOException e) {
            return Version.parse(DEV_FALLBACK);
        }
    }

    /** Pure helper: resolves a {@link Version} from the given properties. */
    static Version fromProperties(Properties props) {
        String raw = props.getProperty("version", "");
        String value = raw == null ? "" : raw.trim();
        return Version.parse(value.isEmpty() ? DEV_FALLBACK : value);
    }
}
