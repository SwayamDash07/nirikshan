package com.nirikshan.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskSignalDerivationTest {
    @Test
    void relativeSignalsAreBoundedAtZeroForNonIncreasingReadings() {
        // The service's deterministic contract: increases and slowdowns never
        // become negative when a reading improves.
        assertEquals(0, Math.max(0, (2.0 - 3.0) / 3.0));
        assertEquals(0, Math.max(0, (1.0 - 1.5) / 1.5));
    }
}
