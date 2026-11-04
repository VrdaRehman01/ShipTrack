package com.shiptrack.auth.exception;

import com.shiptrack.common.error.ApiException;

public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException(String email) {
        super(AuthErrorCode.EMAIL_ALREADY_REGISTERED, "Email is already registered: " + email);
    }
}
