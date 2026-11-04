package com.shiptrack.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * Verifies HS256 JWTs issued by auth-service (signature, issuer, expiry) and turns them into an
 * {@link AuthenticatedUser}. Claims: sub = user id, email, role.
 */
public class JwtVerifier {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";

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
