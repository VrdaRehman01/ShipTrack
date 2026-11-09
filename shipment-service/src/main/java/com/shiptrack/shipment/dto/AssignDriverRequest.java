package com.shiptrack.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record AssignDriverRequest(
        @Schema(example = "12", description = "Id of a user with the DRIVER role") @NotNull @Positive Long driverId,
        @Schema(example = "2026-11-12", description = "Optional: the day the customer can expect the parcel", nullable = true)
        LocalDate expectedDeliveryDate) {
}
