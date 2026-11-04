package com.shiptrack.tracking.exception;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

/** Same error shape as every Spring service (see shiptrack-common ApiError). */
@Serdeable
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        @Nullable String correlationId) {
}
