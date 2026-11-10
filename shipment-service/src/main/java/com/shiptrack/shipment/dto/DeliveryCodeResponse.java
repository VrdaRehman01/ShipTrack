package com.shiptrack.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Returned only to the shipment owner when a receiver code is created or replaced. */
public record DeliveryCodeResponse(
        @Schema(example = "4821", description = "Share with the receiver; this code is required to confirm delivery")
        String deliveryCode) {
}
