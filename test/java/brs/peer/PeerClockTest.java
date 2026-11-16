package brs.peer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

class PeerClockTest {

    @Test
    void offsetMillis_GivenSymmetricRoundTrip_ReturnsMidpointMinusPeerTime() {
        assertEquals(OptionalLong.of(200), PeerClock.offsetMillis(1000, 1200, new JsonPrimitive(900)));
    }

    @Test
    void offsetMillis_GivenPeerAhead_IsNegative() {
        assertEquals(OptionalLong.of(-4900), PeerClock.offsetMillis(1000, 1200, new JsonPrimitive(6000)));
    }

    @Test
    void offsetMillis_GivenMissingOrInvalidTime_IsEmpty() {
        assertEquals(OptionalLong.empty(), PeerClock.offsetMillis(1000, 1200, null));
        assertEquals(OptionalLong.empty(), PeerClock.offsetMillis(1000, 1200, JsonNull.INSTANCE));
        assertEquals(OptionalLong.empty(), PeerClock.offsetMillis(1000, 1200, new JsonPrimitive("abc")));
        assertEquals(OptionalLong.empty(), PeerClock.offsetMillis(1000, 1200, new JsonPrimitive(true)));
        assertEquals(OptionalLong.empty(), PeerClock.offsetMillis(1000, 1200, new JsonObject()));
    }

    @Test
    void offsetMillis_GivenNonIntegralTime_IsEmpty() {
        assertEquals(OptionalLong.empty(), PeerClock.offsetMillis(1000, 1200, new JsonPrimitive(900.5)));
    }

    @Test
    void offsetMillis_GivenReceivedBeforeSent_IsEmpty() {
        assertEquals(OptionalLong.empty(), PeerClock.offsetMillis(1200, 1000, new JsonPrimitive(900)));
    }

    @Test
    void offsetMillis_GivenExtremePeerTime_DoesNotOverflow() {
        OptionalLong peerFarAhead = PeerClock.offsetMillis(1000, 1200, new JsonPrimitive(Long.MAX_VALUE));
        OptionalLong peerFarBehind = PeerClock.offsetMillis(1000, 1200, new JsonPrimitive(Long.MIN_VALUE));

        assertTrue(peerFarAhead.isPresent() && peerFarAhead.getAsLong() < 0);
        assertTrue(peerFarBehind.isPresent() && peerFarBehind.getAsLong() > 0);
    }

    @Test
    void offsetMillis_GivenExtremeLocalTimes_DoesNotOverflow() {
        OptionalLong offset = PeerClock.offsetMillis(Long.MAX_VALUE - 2, Long.MAX_VALUE, new JsonPrimitive(0));

        assertEquals(OptionalLong.of(Long.MAX_VALUE - 1), offset);
    }
}
