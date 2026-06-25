package brs;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildInfoTest {

    @Test
    void fromProperties_givenCleanVersion_returnsReleaseVersion() {
        Properties p = new Properties();
        p.setProperty("version", "3.9.12");

        Version v = BuildInfo.fromProperties(p);

        assertEquals(Version.parse("v3.9.12"), v);
        assertFalse(v.isPrelease());
    }

    @Test
    void fromProperties_givenDevVersion_returnsPrerelease() {
        Properties p = new Properties();
        p.setProperty("version", "3.9.12-dev");

        Version v = BuildInfo.fromProperties(p);

        assertTrue(v.isPrelease());
    }

    @Test
    void fromProperties_givenMissingVersion_fallsBackToDev() {
        Version v = BuildInfo.fromProperties(new Properties());

        assertEquals(Version.parse("0.0.0-dev"), v);
        assertTrue(v.isPrelease());
    }

    @Test
    void fromProperties_givenBlankVersion_fallsBackToDev() {
        Properties p = new Properties();
        p.setProperty("version", "  ");

        Version v = BuildInfo.fromProperties(p);

        assertTrue(v.isPrelease());
    }
}
