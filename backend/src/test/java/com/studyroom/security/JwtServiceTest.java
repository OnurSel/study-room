package com.studyroom.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for anonymous participant token issuance and verification. */
class JwtServiceTest {

    private static final String TEST_SECRET =
            "studyroom-test-only-secret-key-at-least-32-bytes-long";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET);
    }

    @Test
    void issuedTokenCanBeVerifiedAndContainsParticipantIdentity() {
        UUID participantId = UUID.randomUUID();
        Instant beforeIssue = Instant.now();

        String token = jwtService.issueToken(participantId, "Alex");
        JwtService.AnonymousIdentity identity = jwtService.verifyToken(token);

        assertEquals(participantId, identity.participantId());
        assertEquals("Alex", identity.displayName());
        assertTrue(identity.expiresAt().isAfter(beforeIssue.plusSeconds(47 * 60 * 60)));
        assertTrue(identity.expiresAt().isBefore(Instant.now().plusSeconds(48 * 60 * 60 + 5)));
    }

    @Test
    void repeatedIssuanceProducesDifferentTokens() {
        UUID participantId = UUID.randomUUID();

        String first = jwtService.issueToken(participantId, "Alex");
        String second = jwtService.issueToken(participantId, "Alex");

        assertNotEquals(first, second);
        assertEquals(participantId, jwtService.verifyToken(second).participantId());
    }

    @Test
    void rejectsTokensSignedWithAnotherSecret() {
        JwtService anotherIssuer = new JwtService(
                "another-test-only-signing-secret-at-least-32-bytes");
        String token = anotherIssuer.issueToken(UUID.randomUUID(), "Alex");

        assertThrows(JwtException.class, () -> jwtService.verifyToken(token));
    }

    @Test
    void rejectsMalformedAndMissingTokens() {
        assertThrows(IllegalArgumentException.class, () -> jwtService.verifyToken(null));
        assertThrows(IllegalArgumentException.class, () -> jwtService.verifyToken("  "));
        assertThrows(JwtException.class, () -> jwtService.verifyToken("not.a.jwt"));
    }

    @Test
    void rejectsInvalidDisplayNames() {
        UUID participantId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> jwtService.issueToken(participantId, null));
        assertThrows(IllegalArgumentException.class,
                () -> jwtService.issueToken(participantId, " "));
        assertThrows(IllegalArgumentException.class,
                () -> jwtService.issueToken(participantId, "x".repeat(41)));

        assertDoesNotThrow(() -> jwtService.issueToken(participantId, "x".repeat(40)));
    }

    @Test
    void rejectsMissingParticipantId() {
        assertThrows(NullPointerException.class,
                () -> jwtService.issueToken(null, "Alex"));
    }

    @Test
    void rejectsInsufficientSigningSecret() {
        JwtService insecureService = new JwtService("too-short");

        assertThrows(IllegalStateException.class,
                () -> insecureService.issueToken(UUID.randomUUID(), "Alex"));
    }
}
