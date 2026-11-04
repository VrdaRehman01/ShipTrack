package com.shiptrack.billing.service;

import com.shiptrack.billing.config.PricingProperties;
import com.shiptrack.billing.entity.Invoice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/** Prices a delivered shipment. Kept separate so the formula is easy to test and change. */
@Service
public class PricingService {

    private final PricingProperties pricing;

    public PricingService(PricingProperties pricing) {
        this.pricing = pricing;
    }

    public Invoice.Price price(BigDecimal weightKg) {
        BigDecimal base = money(pricing.baseFee());
        BigDecimal weightCharge = money(pricing.perKgRate().multiply(weightKg));
        return new Invoice.Price(base, weightCharge, base.add(weightCharge), pricing.currency());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
