package com.shiptrack.tracking.dto;

import com.shiptrack.tracking.entity.Location;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Serdeable
public record LocationResponse(
        @Schema(example = "901") Long id,
        @Schema(example = "42") Long shipmentId,
        @Schema(example = "12") Long driverId,
        @Schema(example = "24.860966") BigDecimal latitude,
        @Schema(example = "67.010040") BigDecimal longitude,
        @Schema(example = "2026-11-04T12:10:00Z") Instant recordedAt) {

    public static LocationResponse from(Location l) {
        return new LocationResponse(l.id(), l.shipmentId(), l.driverId(), l.latitude(), l.longitude(), l.recordedAt());
    }
}
