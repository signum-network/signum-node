package brs.web.api.http.handler;

import java.util.OptionalInt;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import brs.services.ClockDriftMonitor;

/** Adds the local clock drift estimate to an API response. */
final class ClockDriftFields {

    /** Estimated local clock offset in seconds (negative: behind), or null while unknown. */
    static final String OFFSET = "clockOffsetSeconds";
    static final String DRIFTING = "clockDrifting";
    static final String THRESHOLD = "clockDriftWarnSeconds";
    /** Median local clock offset against peers in seconds (negative: behind), or null while unknown. */
    static final String PEER_OFFSET = "peerClockOffsetSeconds";
    static final String PEER_SAMPLES = "peerClockSamples";

    private ClockDriftFields() {
    }

    static void addTo(JsonObject response, ClockDriftMonitor monitor) {
        addOffset(response, OFFSET, monitor.getEstimatedOffsetSeconds());
        response.addProperty(DRIFTING, monitor.isDrifting());
        response.addProperty(THRESHOLD, monitor.getThresholdSeconds());
        addOffset(response, PEER_OFFSET, monitor.getPeerOffsetSeconds());
        response.addProperty(PEER_SAMPLES, monitor.getPeerSampleCount());
    }

    private static void addOffset(JsonObject response, String field, OptionalInt offset) {
        if (offset.isPresent()) {
            response.addProperty(field, offset.getAsInt());
        } else {
            response.add(field, JsonNull.INSTANCE);
        }
    }
}
