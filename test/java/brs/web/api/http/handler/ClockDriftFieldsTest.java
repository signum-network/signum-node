package brs.web.api.http.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import brs.services.ClockDriftMonitor;

class ClockDriftFieldsTest {

    private ClockDriftMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = mock(ClockDriftMonitor.class);
        when(monitor.getThresholdSeconds()).thenReturn(10);
    }

    @Test
    void addTo_GivenEstimate_AddsOffsetFlagAndThreshold() {
        when(monitor.getEstimatedOffsetSeconds()).thenReturn(OptionalInt.of(-42));
        when(monitor.isDrifting()).thenReturn(true);
        JsonObject response = new JsonObject();

        ClockDriftFields.addTo(response, monitor);

        assertEquals(-42, response.get(ClockDriftFields.OFFSET).getAsInt());
        assertTrue(response.get(ClockDriftFields.DRIFTING).getAsBoolean());
        assertEquals(10, response.get(ClockDriftFields.THRESHOLD).getAsInt());
    }

    @Test
    void addTo_GivenNoEstimateYet_AddsNullOffsetAndNotDrifting() {
        when(monitor.getEstimatedOffsetSeconds()).thenReturn(OptionalInt.empty());
        JsonObject response = new JsonObject();

        ClockDriftFields.addTo(response, monitor);

        assertTrue(response.has(ClockDriftFields.OFFSET));
        assertTrue(response.get(ClockDriftFields.OFFSET).isJsonNull());
        assertFalse(response.get(ClockDriftFields.DRIFTING).getAsBoolean());
    }
}
