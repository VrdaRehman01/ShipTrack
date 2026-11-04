package com.shiptrack.billing.dto;

import com.shiptrack.billing.entity.Invoice;
import com.shiptrack.billing.entity.InvoiceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Money is a decimal string plus currency (API design system), so no client loses precision. */
public record InvoiceResponse(
        @Schema(example = "17") Long id,
        @Schema(example = "INV-2026-000017") String invoiceNumber,
        @Schema(example = "42") Long shipmentId,
        @Schema(example = "ST-2026-000042") String trackingCode,
        @Schema(example = "7") Long customerId,
        @Schema(example = "2.500") String weightKg,
        @Schema(example = "250.00") String baseFee,
        @Schema(example = "300.00") String weightCharge,
        @Schema(example = "550.00") String amount,
        @Schema(example = "PKR") String currency,
        @Schema(example = "ISSUED") InvoiceStatus status,
        @Schema(example = "2026-11-05T15:45:02Z") Instant issuedAt,
        @Schema(example = "2026-11-06T10:00:00Z", nullable = true) Instant paidAt) {

    public static InvoiceResponse from(Invoice i) {
        return new InvoiceResponse(
                i.getId(), i.getInvoiceNumber(), i.getShipmentId(), i.getTrackingCode(), i.getCustomerId(),
                i.getWeightKg().toPlainString(), i.getBaseFee().toPlainString(), i.getWeightCharge().toPlainString(),
                i.getAmount().toPlainString(), i.getCurrency(), i.getStatus(), i.getIssuedAt(), i.getPaidAt());
    }
}
