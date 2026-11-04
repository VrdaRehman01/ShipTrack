package com.shiptrack.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bound from shiptrack.jwt.* — the app refuses to start if the secret is missing or too short. */
@Validated
@ConfigurationProperties(prefix = "shiptrack.jwt")
public record JwtProperties(
        // HS256 needs at least 256 bits = 32 bytes.
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String secret,
        @NotNull Duration expiration,
        @NotBlank String issuer) {
}
