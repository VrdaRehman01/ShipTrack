package com.shiptrack.common.security;

/** The caller, as proven by a valid JWT. Built from token claims only (no database lookup). */
public record AuthenticatedUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }
}
