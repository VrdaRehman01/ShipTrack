package com.shiptrack.auth.service;

import com.shiptrack.auth.entity.User;
import com.shiptrack.common.security.JwtProperties;
import com.shiptrack.common.security.JwtVerifier;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues HS256 JWTs. Claims: sub = user id, email, role, auth_time (when they logged in), plus a unique jti.
 * Every service verifies them with shiptrack-common's JwtVerifier and the same JWT_SECRET.
 */
@Service
public class JwtService {

    /** A replacement token, and how long it lasts (shorter than usual near the end of a session). */
    public record Renewal(String token, long expiresInSeconds) {
    }

    private final SecretKey key;
    private final JwtProperties properties;
    private final JwtVerifier verifier;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = JwtVerifier.signingKey(properties.secret());
        this.properties = properties;
        this.verifier = new JwtVerifier(properties, clock);
        this.clock = clock;
    }

    /** A token for a new login, which starts a new session. */
    public String generateToken(User user) {
        Instant now = clock.instant();
        return build(user, now, now, now.plus(properties.expiration()));
    }

    /**
     * A fresh token in the same session. Keeps the original login time, so repeated renewals can never
     * run past the session limit. Empty once the limit is reached, or for a token from before sessions existed.
     */
    public Optional<Renewal> renew(User user, String currentToken) {
        Instant sessionStart = verifier.sessionStart(currentToken).orElse(null);
        if (sessionStart == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Instant sessionEnd = sessionStart.plus(properties.maxSession());
        if (!now.isBefore(sessionEnd)) {
            return Optional.empty();
        }
        Instant fullExpiry = now.plus(properties.expiration());
        Instant expiry = fullExpiry.isBefore(sessionEnd) ? fullExpiry : sessionEnd;
        return Optional.of(new Renewal(build(user, sessionStart, now, expiry), Duration.between(now, expiry).toSeconds()));
    }

    public long expirationSeconds() {
        return properties.expiration().toSeconds();
    }

    private String build(User user, Instant sessionStart, Instant issuedAt, Instant expiry) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .issuer(properties.issuer())
                .claim(JwtVerifier.CLAIM_EMAIL, user.getEmail())
                .claim(JwtVerifier.CLAIM_ROLE, user.getRole().name())
                .claim(JwtVerifier.CLAIM_SESSION_START, sessionStart.getEpochSecond())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
