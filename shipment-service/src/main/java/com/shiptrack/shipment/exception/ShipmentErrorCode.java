package com.shiptrack.shipment.exception;

import com.shiptrack.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Error codes specific to shipment-service (shared ones live in CommonErrorCode). */
public enum ShipmentErrorCode implements ErrorCode {
    /** FR-5: skipping a step, going backwards, or changing a finished shipment. */
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST),
    /** A shipment can't be picked up before an admin assigns a driver. */
    DRIVER_NOT_ASSIGNED(HttpStatus.BAD_REQUEST),
    /** The user to assign doesn't exist or isn't a DRIVER. */
    INVALID_DRIVER(HttpStatus.BAD_REQUEST),
    /** Delivered or cancelled shipments can't be edited. */
    SHIPMENT_CLOSED(HttpStatus.CONFLICT),
    /** A driver tried to accept a parcel that already has a driver, or is no longer waiting for one. */
    JOB_TAKEN(HttpStatus.CONFLICT),
    /** A driver tried to give back a parcel they have already collected. */
    CANNOT_RELEASE_JOB(HttpStatus.CONFLICT),
    /** expectedVersion didn't match: someone else changed the shipment first. */
    VERSION_MISMATCH(HttpStatus.CONFLICT),
    /** FR-10: same Idempotency-Key reused with a different request body. */
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT);

    private final HttpStatus status;

    ShipmentErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
