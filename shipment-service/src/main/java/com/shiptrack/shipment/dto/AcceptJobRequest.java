package com.shiptrack.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record AcceptJobRequest(
        @Schema(example = "2026-11-12", description = "The day the driver expects to deliver it", nullable = true)
        LocalDate expectedDeliveryDate) {
}
