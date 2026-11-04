package com.shiptrack.common.error;

import org.springframework.http.HttpStatus;

/**
 * A machine-readable error code (UPPER_SNAKE) plus the HTTP status it maps to.
 * Each service defines its own enum implementing this; {@link CommonErrorCode} holds the shared ones.
 */
public interface ErrorCode {

    /** The code clients match on, e.g. INVALID_STATUS_TRANSITION. */
    String name();

    HttpStatus status();
}
