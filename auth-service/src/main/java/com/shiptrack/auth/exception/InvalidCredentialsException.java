package com.shiptrack.auth.exception;

public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        // Same message for "no such email" and "wrong password" so we don't leak which emails exist.
        super(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
    }
}
