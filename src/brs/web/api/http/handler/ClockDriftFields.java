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

    private ClockDriftFields() {
    }

    static void addTo(JsonObject response, ClockDriftMonitor monitor) {
        OptionalInt offset = monitor.getEstimatedOffsetSeconds();
        if (offset.isPresent()) {
            response.addProperty(OFFSET, offset.getAsInt());
        } else {
            response.add(OFFSET, JsonNull.INSTANCE);
        }
        response.addProperty(DRIFTING, monitor.isDrifting());
        response.addProperty(THRESHOLD, monitor.getThresholdSeconds());
    }
}
