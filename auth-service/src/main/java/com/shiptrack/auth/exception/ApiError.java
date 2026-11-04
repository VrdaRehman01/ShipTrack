package com.shiptrack.auth.exception;

import java.time.Instant;

/** The one error shape every ShipTrack service returns. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId) {
}
