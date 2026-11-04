package com.shiptrack.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional first admin account, read from ADMIN_EMAIL / ADMIN_PASSWORD. */
@ConfigurationProperties(prefix = "shiptrack.admin")
public record AdminProperties(String email, String password) {

    public boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
