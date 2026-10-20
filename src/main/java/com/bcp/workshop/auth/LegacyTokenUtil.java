package com.bcp.workshop.auth;

import io.smallrye.jwt.build.Jwt;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class LegacyTokenUtil {
    public static final int TOKEN_LIFETIME_SECONDS = 3600;

    private LegacyTokenUtil() {}

    public static String create(String username, String role) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plusSeconds(TOKEN_LIFETIME_SECONDS);

        return Jwt.claims()
                .subject(username)
                .claim("groups", List.of(role))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .sign();
    }
}
