package brs.peer;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboundContactsTest {

    private final InboundContacts contacts = new InboundContacts();

    @Test
    void record_GivenPublicAddress_UpdatesPublicOnly() {
        contacts.record("8.8.8.8", 100L);
        contacts.record("2001:4860:4860::8888", 200L);

        assertEquals(OptionalLong.of(200L), contacts.lastPublicMillis());
        assertTrue(contacts.lastLocalMillis().isEmpty());
    }

    @Test
    void record_GivenLocalAddress_UpdatesLocalOnly() {
        // LAN, container bridges, carrier-grade NAT, IPv6 link-local and unique-local
        String[] local = {"192.168.1.5", "10.0.0.2", "172.17.0.1", "100.64.1.1", "fe80::1", "fd12::1"};
        long t = 1;
        for (String address : local) {
            contacts.record(address, t++);
        }

        assertEquals(OptionalLong.of(local.length), contacts.lastLocalMillis());
        assertTrue(contacts.lastPublicMillis().isEmpty());
    }

    @Test
    void record_GivenLoopbackOrUnparseable_Ignores() {
        // a host name would mean a DNS lookup on the request thread: such strings are not parsed
        for (String address : new String[]{"127.0.0.1", "::1", "0.0.0.0", "not-an-ip", "example.com", null}) {
            contacts.record(address, 100L);
        }

        assertTrue(contacts.lastPublicMillis().isEmpty());
        assertTrue(contacts.lastLocalMillis().isEmpty());
    }

    @Test
    void record_GivenOlderTimestamp_KeepsNewest() {
        contacts.record("8.8.8.8", 500L);
        contacts.record("1.1.1.1", 300L);

        assertEquals(OptionalLong.of(500L), contacts.lastPublicMillis());
    }

    @Test
    void lastPublicMillis_GivenNothingRecorded_IsEmpty() {
        assertTrue(contacts.lastPublicMillis().isEmpty());
        assertTrue(contacts.lastLocalMillis().isEmpty());
    }
}
