package com.shiptrack.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignDriverRequest(
        @Schema(example = "12", description = "Id of a user with the DRIVER role") @NotNull @Positive Long driverId) {
}
