package com.shiptrack.billing.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * shiptrack.pricing.* — invoice amount = baseFee + perKgRate x weight, rounded half-up to 2 decimals.
 * v1 bills in one currency (PKR); see ADR 0005.
 */
@Validated
@ConfigurationProperties(prefix = "shiptrack.pricing")
public record PricingProperties(
        @DefaultValue("PKR") @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @DefaultValue("250.00") @NotNull @DecimalMin("0") BigDecimal baseFee,
        @DefaultValue("120.00") @NotNull @DecimalMin("0") BigDecimal perKgRate) {
}
