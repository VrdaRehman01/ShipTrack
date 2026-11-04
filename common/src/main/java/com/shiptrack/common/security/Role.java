package com.shiptrack.common.security;

/** User roles, carried in the JWT "role" claim. */
public enum Role {
    CUSTOMER,
    DRIVER,
    ADMIN;

    /** Spring Security authority name, e.g. ROLE_ADMIN. */
    public String authority() {
        return "ROLE_" + name();
    }
}
