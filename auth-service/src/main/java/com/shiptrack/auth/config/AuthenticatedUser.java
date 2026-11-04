package com.shiptrack.auth.config;

import com.shiptrack.auth.entity.Role;

/** The principal we put in the SecurityContext after a valid JWT. Built from token claims only. */
public record AuthenticatedUser(Long id, String email, Role role) {
}
