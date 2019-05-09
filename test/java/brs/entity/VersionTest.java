package brs.entity;

import brs.Version;
import org.junit.Test;

import static org.junit.Assert.*;

public class VersionTest {
    @Test
    public void testVersionParse() {
        String[] validVersions = {
                "2.2.2",
                "v1.2.3",
                "v0.2.7-dev",
                "1.24.2-rc9",
                "v0.8.999-beta99"
        };

        String[] invalidVersions = {
                "v2.0",
                "v1.2.3-abc123",
                "v1a.2.3-dev",
                "1.0-dev",
                "v1.2.3-123dev123",
                "",
                null
        };

        for (String versionString : validVersions) {
            Version version = Version.parse(versionString);
            if (versionString.startsWith("v")) {
                assertEquals(versionString, version.toString());
            } else {
                assertEquals("v" + versionString, version.toString());
            }
        }

        for (String versionString : invalidVersions) {
            try {
                Version version = Version.parse(versionString);
                if (version == Version.EMPTY) throw new IllegalArgumentException();
                throw new AssertionError("Did not fail to parse: " + versionString);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Test
    public void testVersionCompare() {
        Version lower = Version.parse("v1.0.0");
        Version equal = Version.parse("v1.0.0");
        Version higher = Version.parse("v1.0.1");
        Version higherPreRelease = Version.parse("v1.5.0-dev");

        assertFalse(lower.isGreaterThan(equal));
        assertTrue(lower.isGreaterThanOrEqualTo(equal));
        assertFalse(lower.isGreaterThanOrEqualTo(higher));
        assertTrue(higher.isGreaterThan(lower));
        assertFalse(higher.isGreaterThanOrEqualTo(higherPreRelease));
        assertTrue(higherPreRelease.isGreaterThan(higher));
        assertFalse(higher.isPrelease());
        assertTrue(higherPreRelease.isPrelease());
        assertTrue(higherPreRelease.isGreaterThanOrEqualTo(higherPreRelease));
    }
}
