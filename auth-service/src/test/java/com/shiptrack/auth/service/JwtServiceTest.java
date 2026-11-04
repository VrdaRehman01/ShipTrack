package com.shiptrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shiptrack.auth.entity.User;
import com.shiptrack.common.security.AuthenticatedUser;
import com.shiptrack.common.security.JwtProperties;
import com.shiptrack.common.security.JwtVerifier;
import com.shiptrack.common.security.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Tokens issued by auth-service must verify with the shared JwtVerifier every other service uses. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-at-least-32-characters-long";
    private static final Instant NOW = Instant.parse("2026-11-04T09:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final JwtProperties props = new JwtProperties(SECRET, Duration.ofMinutes(15), "shiptrack-auth");
    private final JwtService jwtService = new JwtService(props, CLOCK);
    private final JwtVerifier verifier = new JwtVerifier(props, CLOCK);

    @Test
    void issuedTokenVerifiesBackToSameUser() {
        String token = jwtService.generateToken(user(42L, Role.DRIVER));

        AuthenticatedUser parsed = verifier.verify(token);

        assertThat(parsed.id()).isEqualTo(42L);
        assertThat(parsed.email()).isEqualTo("user@example.com");
        assertThat(parsed.role()).isEqualTo(Role.DRIVER);
    }

    @Test
    void expiresAfterFifteenMinutes() {
        String token = jwtService.generateToken(user(1L, Role.CUSTOMER));
        JwtVerifier sixteenMinutesLater =
                new JwtVerifier(props, Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> sixteenMinutesLater.verify(token)).isInstanceOf(ExpiredJwtException.class);
        assertThat(jwtService.expirationSeconds()).isEqualTo(900);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtService other = new JwtService(
                new JwtProperties("a-completely-different-secret-32-chars-min!", Duration.ofMinutes(15), "shiptrack-auth"),
                CLOCK);
        String foreignToken = other.generateToken(user(1L, Role.ADMIN));

        assertThatThrownBy(() -> verifier.verify(foreignToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        JwtService otherIssuer = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(15), "someone-else"), CLOCK);
        String token = otherIssuer.generateToken(user(1L, Role.ADMIN));

        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(user(1L, Role.CUSTOMER));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        assertThatThrownBy(() -> verifier.verify(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void eachTokenHasUniqueId() {
        User user = user(1L, Role.CUSTOMER);
        assertThat(jwtService.generateToken(user)).isNotEqualTo(jwtService.generateToken(user));
    }

    @Test
    void refusesShortSecret() {
        JwtProperties weak = new JwtProperties("too-short", Duration.ofMinutes(15), "shiptrack-auth");
        assertThatThrownBy(() -> new JwtService(weak, Clock.systemUTC())).isInstanceOf(WeakKeyException.class);
    }

    private static User user(Long id, Role role) {
        User user = new User("user@example.com", "hash", "Test User", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
