package com.shiptrack.billing.exception;

import com.shiptrack.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Error codes specific to billing-service (shared ones live in CommonErrorCode). */
public enum BillingErrorCode implements ErrorCode {
    /** Paying an invoice that is already paid. */
    INVOICE_ALREADY_PAID(HttpStatus.CONFLICT),
    /** The same shipment was invoiced before with different details. */
    INVOICE_MISMATCH(HttpStatus.CONFLICT);

    private final HttpStatus status;

    BillingErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
