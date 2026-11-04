package com.shiptrack.auth.entity;

public enum Role {
    CUSTOMER,
    DRIVER,
    ADMIN;

    /** Spring Security authority name, e.g. ROLE_ADMIN. */
    public String authority() {
        return "ROLE_" + name();
    }
}
