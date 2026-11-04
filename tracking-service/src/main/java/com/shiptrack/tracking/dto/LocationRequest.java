package com.shiptrack.tracking.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

@Serdeable
public record LocationRequest(
        @Schema(example = "24.860966") @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 6)
        BigDecimal latitude,
        @Schema(example = "67.010040") @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6)
        BigDecimal longitude,
        @Schema(description = "When the device took the reading; defaults to now. Can't be in the future or over 24h old.",
                example = "2026-11-04T12:10:00Z", nullable = true)
        @Nullable Instant recordedAt) {
}
