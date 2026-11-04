package com.shiptrack.auth.service;

import com.shiptrack.auth.entity.User;
import com.shiptrack.common.security.JwtProperties;
import com.shiptrack.common.security.JwtVerifier;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues HS256 JWTs. Claims: sub = user id, email, role, plus a unique jti.
 * Every service verifies them with shiptrack-common's JwtVerifier and the same JWT_SECRET.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = JwtVerifier.signingKey(properties.secret());
        this.properties = properties;
        this.clock = clock;
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .issuer(properties.issuer())
                .claim(JwtVerifier.CLAIM_EMAIL, user.getEmail())
                .claim(JwtVerifier.CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiration())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long expirationSeconds() {
        return properties.expiration().toSeconds();
    }
}
