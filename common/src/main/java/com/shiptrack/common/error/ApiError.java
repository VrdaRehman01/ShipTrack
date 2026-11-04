package com.shiptrack.common.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** The one error shape every ShipTrack service returns. */
@Schema(description = "Standard error response")
public record ApiError(
        @Schema(example = "2026-11-04T09:30:00Z") Instant timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "INVALID_STATUS_TRANSITION") String error,
        @Schema(example = "Cannot move shipment from CREATED to DELIVERED") String message,
        @Schema(example = "/api/v1/shipments/42/status") String path,
        @Schema(example = "a1b2c3d4") String correlationId) {
}
