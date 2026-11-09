package com.shiptrack.shipment.dto;

import com.shiptrack.shipment.entity.Shipment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * What a driver sees before taking a parcel: enough to decide, but no names or phone numbers.
 * Those come with the full shipment once the job is theirs.
 */
public record AvailableJobResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "House 12, Street 4, F-7/2, Islamabad") String pickupAddress,
        @Schema(example = "Flat 3B, Clifton Block 5, Karachi") String deliveryAddress,
        @Schema(example = "2.500") String weightKg,
        @Schema(example = "Books, fragile", nullable = true) String description,
        @Schema(example = "2026-11-04T09:30:00Z") Instant createdAt) {

    public static AvailableJobResponse from(Shipment s) {
        return new AvailableJobResponse(s.getId(), s.getPickupAddress(), s.getDeliveryAddress(),
                s.getWeightKg().toPlainString(), s.getDescription(), s.getCreatedAt());
    }
}
