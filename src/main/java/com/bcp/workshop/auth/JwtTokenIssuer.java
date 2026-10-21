package com.bcp.workshop.auth;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Set;

@ApplicationScoped
public class JwtTokenIssuer {
    static final long LIFETIME_SECONDS = 3600;

    public IssuedToken issue(String username, String role, String client) {
        Instant issuedAt = Instant.now();
        String token = Jwt.subject(username)
                .claim("client", client)
                .groups(Set.of(role))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(LIFETIME_SECONDS))
                .sign();
        return new IssuedToken(token, LIFETIME_SECONDS);
    }

    public record IssuedToken(String token, long expiresIn) {
    }
}
