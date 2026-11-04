package com.shiptrack.tracking.exception;

import io.micronaut.http.HttpStatus;

/** An expected error with a ShipTrack error code; rendered in the standard error shape. */
public class TrackingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public TrackingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public TrackingException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static TrackingException forbidden(String message) {
        return new TrackingException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static TrackingException notFound(String message) {
        return new TrackingException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
