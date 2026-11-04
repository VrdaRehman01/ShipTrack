package com.shiptrack.shipment.dto;

import com.shiptrack.shipment.entity.Shipment;
import com.shiptrack.shipment.entity.ShipmentStatus;
import com.shiptrack.shipment.entity.ShipmentStatusChange;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * What anyone with the tracking code may see: status and timeline only.
 * No names, phones, addresses or ids, so a guessed code leaks nothing personal.
 */
public record PublicTrackingResponse(
        @Schema(example = "ST-2026-000042") String trackingCode,
        @Schema(example = "IN_TRANSIT") ShipmentStatus status,
        @Schema(example = "2026-11-04T12:10:00Z") Instant lastUpdated,
        List<Step> timeline) {

    public record Step(
            @Schema(example = "PICKED_UP") ShipmentStatus status,
            @Schema(example = "2026-11-04T10:05:00Z") Instant at) {
    }

    public static PublicTrackingResponse from(Shipment s, List<ShipmentStatusChange> changes) {
        return new PublicTrackingResponse(
                s.getTrackingCode(),
                s.getStatus(),
                s.getUpdatedAt(),
                changes.stream().map(c -> new Step(c.getToStatus(), c.getChangedAt())).toList());
    }
}
