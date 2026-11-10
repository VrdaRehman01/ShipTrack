package com.shiptrack.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Customer-facing estimate; decimal values stay strings to avoid client-side money rounding. */
public record PriceQuoteResponse(
        @Schema(example = "PKR") String currency,
        @Schema(example = "250.00") String baseFee,
        @Schema(example = "120.00") String perKgRate,
        @Schema(example = "300.00") String weightCharge,
        @Schema(example = "550.00") String total) {
}
