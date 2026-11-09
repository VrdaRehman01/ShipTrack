package com.shiptrack.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;

/**
 * Verifies HS256 JWTs issued by auth-service (signature, issuer, expiry) and turns them into an
 * {@link AuthenticatedUser}. Claims: sub = user id, email, role.
 */
public class JwtVerifier {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_SESSION_START = "auth_time";

    private final JwtParser parser;

    public JwtVerifier(JwtProperties properties, Clock clock) {
        SecretKey key = signingKey(properties.secret());
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    /** The HMAC key for a secret; shared with auth-service so both sides derive the same key. */
    public static SecretKey signingKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * When the user logged in to get this token; empty for tokens issued before sessions could be renewed.
     *
     * @throws JwtException if the token is invalid in any way
     */
    public Optional<Instant> sessionStart(String token) {
        return parser.parseSignedClaims(token).getPayload().get(CLAIM_SESSION_START) instanceof Number seconds
                ? Optional.of(Instant.ofEpochSecond(seconds.longValue()))
                : Optional.empty();
    }

    /**
     * @throws JwtException if the token is invalid in any way
     */
    public AuthenticatedUser verify(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        try {
            return new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("Token has missing or invalid claims", e);
        }
    }
}
