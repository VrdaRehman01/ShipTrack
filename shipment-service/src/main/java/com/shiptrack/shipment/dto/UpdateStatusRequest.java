package com.shiptrack.shipment.dto;

import com.shiptrack.shipment.entity.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStatusRequest(
        @Schema(example = "PICKED_UP") @NotNull ShipmentStatus status,
        @Schema(example = "Collected from reception", nullable = true) @Size(max = 255) String note,
        @Schema(description = "Optional: the version you last saw. If someone changed the shipment since, you get 409.",
                example = "0", nullable = true)
        Long expectedVersion,
        @Schema(description = "Receiver's four-digit proof-of-delivery code. Required only for DELIVERED.",
                example = "4821", nullable = true)
        @Pattern(regexp = "^\\d{4}$", message = "deliveryCode: must be exactly four digits") String deliveryCode) {

    public UpdateStatusRequest(ShipmentStatus status, String note, Long expectedVersion) {
        this(status, note, expectedVersion, null);
    }
}
