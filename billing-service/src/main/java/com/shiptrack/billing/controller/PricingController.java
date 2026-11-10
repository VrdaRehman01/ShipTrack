package com.shiptrack.billing.controller;

import com.shiptrack.billing.config.PricingProperties;
import com.shiptrack.billing.dto.PriceQuoteResponse;
import com.shiptrack.billing.entity.Invoice;
import com.shiptrack.billing.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The public quote uses the same pricing service as invoices, so the displayed fare follows configuration. */
@RestController
@RequestMapping("/api/v1/public/pricing")
@Validated
public class PricingController {

    private final PricingProperties pricing;
    private final PricingService pricingService;

    public PricingController(PricingProperties pricing, PricingService pricingService) {
        this.pricing = pricing;
        this.pricingService = pricingService;
    }

    @GetMapping("/quote")
    @Operation(summary = "Estimate a parcel fare using the current invoice pricing")
    public PriceQuoteResponse quote(
            @Parameter(example = "2.500") @RequestParam
            @NotNull @DecimalMin("0.001") @DecimalMax("1000") @Digits(integer = 4, fraction = 3) BigDecimal weightKg) {
        Invoice.Price price = pricingService.price(weightKg);
        return new PriceQuoteResponse(price.currency(), price.baseFee().toPlainString(),
                pricing.perKgRate().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                price.weightCharge().toPlainString(), price.total().toPlainString());
    }
}
