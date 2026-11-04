package com.shiptrack.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shiptrack.billing.config.PricingProperties;
import com.shiptrack.billing.entity.Invoice;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingServiceTest {

    private final PricingService pricing =
            new PricingService(new PricingProperties("PKR", new BigDecimal("250.00"), new BigDecimal("120.00")));

    @ParameterizedTest(name = "{0} kg -> {2} PKR")
    @CsvSource({
        "2.5,    300.00, 550.00",
        "1,      120.00, 370.00",
        "0.001,  0.12,   250.12",
        "0.004,  0.48,   250.48",
        "10.125, 1215.00, 1465.00",
        "0.0042, 0.50,   250.50"   // 0.504 rounds half-up to 0.50
    })
    void amountIsBasePlusWeightRoundedToCents(String weight, String weightCharge, String total) {
        Invoice.Price price = pricing.price(new BigDecimal(weight));

        assertThat(price.baseFee()).isEqualByComparingTo("250.00");
        assertThat(price.weightCharge()).isEqualTo(new BigDecimal(weightCharge).setScale(2));
        assertThat(price.total()).isEqualTo(new BigDecimal(total).setScale(2));
        assertThat(price.total().scale()).isEqualTo(2);
        assertThat(price.currency()).isEqualTo("PKR");
    }
}
