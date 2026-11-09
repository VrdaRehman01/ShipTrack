package com.shiptrack.shipment.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CreateShipmentRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsLongestPhoneTheDatabaseColumnCanHold() {
        assertThat(VALIDATOR.validate(withSenderPhone("+" + "1".repeat(19)))).isEmpty();
    }

    @Test
    void rejectsPhoneLongerThanTheDatabaseColumn() {
        assertThat(VALIDATOR.validate(withSenderPhone("+" + "1".repeat(20)))).hasSize(1);
    }

    private static CreateShipmentRequest withSenderPhone(String phone) {
        return new CreateShipmentRequest("Sara", phone, "A", "Ali", "+92 321 7654321", "B", new BigDecimal("2.5"), null);
    }
}
