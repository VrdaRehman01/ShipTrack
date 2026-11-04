package com.shiptrack.auth.exception;

public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException(String email) {
        super(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email is already registered: " + email);
    }
}
