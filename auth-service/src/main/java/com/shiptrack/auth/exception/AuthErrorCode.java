package com.shiptrack.auth.exception;

import com.shiptrack.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Error codes specific to auth-service (shared ones live in CommonErrorCode). */
public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT);

    private final HttpStatus status;

    AuthErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
