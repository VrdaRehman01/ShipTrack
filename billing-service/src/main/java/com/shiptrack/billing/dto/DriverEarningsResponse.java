package com.shiptrack.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Accrued share of the completed deliveries assigned to this driver. */
public record DriverEarningsResponse(
        long completedDeliveries,
        @Schema(example = "PKR") String currency,
        @Schema(example = "1100.00") String grossFare,
        @Schema(example = "440.00") String earnedAmount,
        @Schema(example = "40.00") String effectiveSharePercent) {
}
