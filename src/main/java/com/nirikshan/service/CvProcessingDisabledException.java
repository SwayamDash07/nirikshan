package com.nirikshan.service;

/**
 * Raised when a caller attempts to make the web backend execute local CV work.
 * CV execution is deliberately an external/local-machine concern.
 */
public class CvProcessingDisabledException extends RuntimeException {
    public CvProcessingDisabledException(String operation) {
        super(operation + " is disabled in the Spring Boot backend. Run CV locally and POST risk events to /api/risk-events.");
    }
}
