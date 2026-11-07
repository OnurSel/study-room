package com.studyroom.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * Issues and verifies short-lived, signed tokens for anonymous participants.
 *
 * Configure STUDYROOM_JWT_SECRET with a random secret of at least 32 UTF-8
 * bytes before enabling anonymous authentication. No default signing secret
 * is shipped with the application.
 */
@Service
public class JwtService {

    private static final String ISSUER = "studyroom";
    private static final String DISPLAY_NAME_CLAIM = "displayName";
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(48);

    private final String configuredSecret;

    public JwtService(@Value("${STUDYROOM_JWT_SECRET:}") String configuredSecret) {
        this.configuredSecret = Objects.requireNonNull(configuredSecret);
    }

    /** Returns a signed bearer token for a newly created anonymous identity. */
    public String issueToken(UUID participantId, String displayName) {
        Objects.requireNonNull(participantId, "participantId");
        if (displayName == null || displayName.isBlank() || displayName.length() > 40) {
            throw new IllegalArgumentException("Display name must contain 1 to 40 characters");
        }

        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(participantId.toString())
                .id(UUID.randomUUID().toString())
                .claim(DISPLAY_NAME_CLAIM, displayName)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_LIFETIME)))
                .signWith(signingKey())
                .compact();
    }

    /**
     * Verifies signature, issuer and expiration, then returns token identity.
     * Throws JwtException (or IllegalArgumentException) for invalid tokens.
     */
    public AnonymousIdentity verifyToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be blank");
        }

        Jws<Claims> parsed = Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token);

        Claims claims = parsed.getPayload();
        if (claims.getExpiration() == null || claims.getSubject() == null) {
            throw new JwtException("Missing required identity claims");
        }

        String displayName = claims.get(DISPLAY_NAME_CLAIM, String.class);
        if (displayName == null || displayName.isBlank() || displayName.length() > 40) {
            throw new JwtException("Invalid display name claim");
        }

        try {
            return new AnonymousIdentity(
                    UUID.fromString(claims.getSubject()),
                    displayName,
                    claims.getExpiration().toInstant());
        } catch (IllegalArgumentException ex) {
            throw new JwtException("Invalid participant ID", ex);
        }
    }

    private SecretKey signingKey() {
        byte[] secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "STUDYROOM_JWT_SECRET must contain at least 32 UTF-8 bytes; " +
                    "set a securely generated secret in the server environment");
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

    public record AnonymousIdentity(UUID participantId, String displayName, Instant expiresAt) {
    }
}
