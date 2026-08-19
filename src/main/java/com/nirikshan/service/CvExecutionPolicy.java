package com.nirikshan.service;

import org.springframework.stereotype.Component;

/** Central guard keeping all Python/video execution outside the backend process. */
@Component
public class CvExecutionPolicy {
    public boolean backendProcessingEnabled() {
        return false;
    }

    public void requireBackendProcessingEnabled(String operation) {
        if (!backendProcessingEnabled()) {
            throw new CvProcessingDisabledException(operation);
        }
    }
}
