package com.shiptrack.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Sent by shipment-service (internal API) when a shipment is delivered. */
public record CreateInvoiceRequest(
        @NotNull @Positive Long shipmentId,
        @NotBlank @Size(max = 20) String trackingCode,
        @NotNull @Positive Long customerId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 5, fraction = 3) BigDecimal weightKg,
        @Positive Long driverId,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal driverPayoutPercent) {

    public CreateInvoiceRequest(Long shipmentId, String trackingCode, Long customerId, BigDecimal weightKg) {
        this(shipmentId, trackingCode, customerId, weightKg, null, null);
    }
}
