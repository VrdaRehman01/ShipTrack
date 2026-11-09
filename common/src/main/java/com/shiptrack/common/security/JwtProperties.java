package com.shiptrack.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Bound from shiptrack.jwt.* — a service refuses to start if the secret is missing or too short. */
@Validated
@ConfigurationProperties(prefix = "shiptrack.jwt")
public record JwtProperties(
        // HS256 needs at least 256 bits = 32 bytes.
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String secret,
        @DefaultValue("15m") Duration expiration,
        @DefaultValue("shiptrack-auth") String issuer,
        // How long after logging in a session can keep being renewed; then a new login is required.
        @DefaultValue("8h") Duration maxSession) {
}
