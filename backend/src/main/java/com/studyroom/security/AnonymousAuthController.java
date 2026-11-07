package com.studyroom.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates an anonymous participant identity without requiring an account.
 * The signed token is short-lived and must be sent as a Bearer token on
 * subsequent authenticated requests.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AnonymousAuthController {

    private final JwtService jwtService;

    public AnonymousAuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/anonymous")
    public ResponseEntity<AnonymousAuthResponse> createAnonymousIdentity(
            @Valid @RequestBody AnonymousAuthRequest request) {

        String displayName = request.displayName().trim();
        UUID participantId = UUID.randomUUID();
        String token = jwtService.issueToken(participantId, displayName);
        Instant expiresAt = jwtService.verifyToken(token).expiresAt();

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new AnonymousAuthResponse(participantId, displayName, token,
                        "Bearer", expiresAt));
    }

    public record AnonymousAuthRequest(
            @NotBlank(message = "Display name is required")
            @Size(max = 40, message = "Display name must be at most 40 characters")
            String displayName) {
    }

    public record AnonymousAuthResponse(
            UUID participantId,
            String displayName,
            String token,
            String tokenType,
            Instant expiresAt) {
    }
}
