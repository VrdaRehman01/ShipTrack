package com.shiptrack.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateShipmentRequest(
        @Schema(example = "Sara Ahmed") @NotBlank @Size(max = 100) String senderName,
        @Schema(example = "+92 300 1234567") @NotBlank @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String senderPhone,
        @Schema(example = "House 12, Street 4, F-7/2, Islamabad") @NotBlank @Size(max = 255) String pickupAddress,
        @Schema(example = "Ali Khan") @NotBlank @Size(max = 100) String receiverName,
        @Schema(example = "+92 321 7654321") @NotBlank @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String receiverPhone,
        @Schema(example = "Flat 3B, Clifton Block 5, Karachi") @NotBlank @Size(max = 255) String deliveryAddress,
        @Schema(example = "2.500", description = "Parcel weight in kg (max 1000)")
        @NotNull @DecimalMin(value = "0.001") @DecimalMax(value = "1000") @Digits(integer = 4, fraction = 3)
        BigDecimal weightKg,
        @Schema(example = "Books, fragile") @Size(max = 255) String description) {

    // At most 20 characters in total, the size of the database column.
    static final String PHONE = "^\\+?[0-9][0-9 \\-]{6,18}$";
    static final String PHONE_MESSAGE = "must be a phone number like +92 300 1234567";
}
