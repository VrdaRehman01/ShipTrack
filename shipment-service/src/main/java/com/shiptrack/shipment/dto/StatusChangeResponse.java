package com.shiptrack.shipment.dto;

import com.shiptrack.common.security.Role;
import com.shiptrack.shipment.entity.ShipmentStatus;
import com.shiptrack.shipment.entity.ShipmentStatusChange;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record StatusChangeResponse(
        @Schema(example = "CREATED", nullable = true) ShipmentStatus fromStatus,
        @Schema(example = "PICKED_UP") ShipmentStatus toStatus,
        @Schema(example = "12") Long changedBy,
        @Schema(example = "DRIVER") Role changedByRole,
        @Schema(example = "Collected from reception", nullable = true) String note,
        @Schema(example = "2026-11-04T10:05:00Z") Instant changedAt) {

    public static StatusChangeResponse from(ShipmentStatusChange c) {
        return new StatusChangeResponse(
                c.getFromStatus(), c.getToStatus(), c.getChangedBy(), c.getChangedByRole(), c.getNote(), c.getChangedAt());
    }
}
