package brs.web.api.http.common;

import brs.props.PropertyService;
import brs.props.Props;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminKeysTest {

    private static final String GOOD_KEY = "3f9a1c7e5b2d8f4a6c0e9b1d3f5a7c2e4b6d8f0a";
    private static final String OTHER_KEY = "9c2e4b6d8f0a3f9a1c7e5b2d8f4a6c0e9b1d3f5a";

    private static AdminKeys keys(String... configured) {
        PropertyService propertyService = mock(PropertyService.class);
        when(propertyService.getStringList(Props.API_ADMIN_KEY_LIST)).thenReturn(new ArrayList<>(List.of(configured)));
        return AdminKeys.from(propertyService);
    }

    @Test
    void isAllowed_GivenConfiguredKey_ReturnsTrue() {
        AdminKeys adminKeys = keys(GOOD_KEY, OTHER_KEY);

        assertTrue(adminKeys.isAllowed(GOOD_KEY));
        assertTrue(adminKeys.isAllowed(OTHER_KEY));
    }

    @Test
    void isAllowed_GivenNullEmptyOrWrongKey_ReturnsFalse() {
        AdminKeys adminKeys = keys(GOOD_KEY);

        assertFalse(adminKeys.isAllowed(null));
        assertFalse(adminKeys.isAllowed(""));
        assertFalse(adminKeys.isAllowed("wrong"));
    }

    @Test
    void isAllowed_GivenPrefixOrExtensionOfKey_ReturnsFalse() {
        AdminKeys adminKeys = keys(GOOD_KEY);

        assertFalse(adminKeys.isAllowed(GOOD_KEY.substring(0, 20)));
        assertFalse(adminKeys.isAllowed(GOOD_KEY + "0"));
    }

    @Test
    void isAllowed_GivenNoKeysConfigured_ReturnsFalse() {
        AdminKeys adminKeys = keys();

        assertFalse(adminKeys.isAllowed(""));
        assertFalse(adminKeys.isAllowed(null));
        assertFalse(adminKeys.isAllowed(GOOD_KEY));
    }

    @Test
    void isAllowed_GivenExampleKeyConfigured_RejectsItButAcceptsOthers() {
        // the example from node-default.properties is public: it must never open the admin API
        AdminKeys adminKeys = keys(AdminKeys.EXAMPLE_KEY, GOOD_KEY);

        assertFalse(adminKeys.isAllowed(AdminKeys.EXAMPLE_KEY));
        assertTrue(adminKeys.isAllowed(GOOD_KEY));
    }

    @Test
    void describeWeaknesses_GivenExampleAndShortKey_NamesPositionsNotKeys() {
        List<String> messages = AdminKeys.describeWeaknesses(List.of(GOOD_KEY, AdminKeys.EXAMPLE_KEY, "short"));

        assertEquals(List.of(
                "API.adminKeyList entry 2 is the example key from node-default.properties and is ignored; replace it",
                "API.adminKeyList entry 3 is shorter than 32 characters; use a long random key"), messages);
        for (String message : messages) {
            assertFalse(message.contains(AdminKeys.EXAMPLE_KEY));
            assertFalse(message.contains("short;") || message.endsWith("short"));
        }
    }

    @Test
    void describeWeaknesses_GivenStrongKeys_ReturnsEmpty() {
        assertTrue(AdminKeys.describeWeaknesses(List.of(GOOD_KEY, OTHER_KEY)).isEmpty());
    }

    @Test
    void isExampleKey_GivenExampleKey_ReturnsTrue() {
        assertTrue(AdminKeys.isExampleKey(AdminKeys.EXAMPLE_KEY));
        assertFalse(AdminKeys.isExampleKey(GOOD_KEY));
    }

    @Test
    void defaultConfig_GivenAdminKeyLineOnlyUncommented_UnlocksNothing() throws IOException {
        // an operator who only uncomments the documented line must not get a published key
        String line = Files.readAllLines(Path.of("conf/node-default.properties")).stream()
                .filter(l -> l.matches("^#\\s*API\\.adminKeyList\\s*=.*"))
                .findFirst().orElseThrow();
        String value = line.substring(line.indexOf('=') + 1);
        String[] entries = Arrays.stream(value.split(";")).map(String::trim).filter(e -> !e.isEmpty()).toArray(String[]::new);

        AdminKeys adminKeys = keys(entries);

        for (String entry : entries) {
            assertFalse(adminKeys.isAllowed(entry), entry);
        }
    }
}
