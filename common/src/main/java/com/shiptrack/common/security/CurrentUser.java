package com.shiptrack.common.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the caller outside controllers (controllers use @AuthenticationPrincipal). */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The caller's raw JWT, for forwarding to another service on their behalf. */
    public static Optional<String> bearerToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser && auth.getCredentials() instanceof String t) {
            return Optional.of(t);
        }
        return Optional.empty();
    }
}
