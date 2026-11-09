package com.shiptrack.shipment.dto;

import com.shiptrack.shipment.entity.Shipment;
import com.shiptrack.shipment.entity.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

public record ShipmentResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "ST-2026-000042") String trackingCode,
        @Schema(example = "IN_TRANSIT") ShipmentStatus status,
        @Schema(example = "7") Long customerId,
        @Schema(example = "12", nullable = true) Long driverId,
        @Schema(example = "Sara Ahmed") String senderName,
        @Schema(example = "+92 300 1234567") String senderPhone,
        @Schema(example = "House 12, Street 4, F-7/2, Islamabad") String pickupAddress,
        @Schema(example = "Ali Khan") String receiverName,
        @Schema(example = "+92 321 7654321") String receiverPhone,
        @Schema(example = "Flat 3B, Clifton Block 5, Karachi") String deliveryAddress,
        @Schema(example = "2.500", description = "Decimal string to avoid rounding") String weightKg,
        @Schema(example = "Books, fragile") String description,
        @Schema(description = "True once billing-service has issued the invoice") boolean invoiced,
        @Schema(description = "Send back as expectedVersion to detect concurrent edits", example = "3") long version,
        @Schema(example = "2026-11-04T09:30:00Z") Instant createdAt,
        @Schema(example = "2026-11-04T12:10:00Z") Instant updatedAt,
        @Schema(example = "2026-11-05T15:45:00Z", nullable = true) Instant deliveredAt,
        @Schema(example = "Bilal Ahmed", nullable = true) String driverName,
        @Schema(example = "+92 311 7654321", nullable = true) String driverPhone,
        @Schema(example = "2026-11-12", nullable = true) LocalDate expectedDeliveryDate) {

    public static ShipmentResponse from(Shipment s) {
        return new ShipmentResponse(
                s.getId(), s.getTrackingCode(), s.getStatus(), s.getCustomerId(), s.getDriverId(),
                s.getSenderName(), s.getSenderPhone(), s.getPickupAddress(),
                s.getReceiverName(), s.getReceiverPhone(), s.getDeliveryAddress(),
                s.getWeightKg().toPlainString(), s.getDescription(), s.isInvoiced(), s.getVersion(),
                s.getCreatedAt(), s.getUpdatedAt(), s.getDeliveredAt(),
                s.getDriverName(), s.getDriverPhone(), s.getExpectedDeliveryDate());
    }
}
