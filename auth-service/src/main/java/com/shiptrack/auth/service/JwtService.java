package com.shiptrack.auth.service;

import com.shiptrack.auth.config.AuthenticatedUser;
import com.shiptrack.auth.config.JwtProperties;
import com.shiptrack.auth.entity.Role;
import com.shiptrack.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256 JWTs. Claims: sub = user id, email, role.
 * Other services verify tokens with the same shared secret (JWT_SECRET).
 */
@Service
public class JwtService {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final Duration expiration;
    private final String issuer;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.expiration();
        this.issuer = properties.issuer();
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(issuer)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry, then returns the user the token was issued to.
     *
     * @throws JwtException if the token is invalid in any way
     */
    public AuthenticatedUser parse(String token) {
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

    public long expirationSeconds() {
        return expiration.toSeconds();
    }
}
