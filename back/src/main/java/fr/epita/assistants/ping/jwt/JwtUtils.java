package fr.epita.assistants.ping.jwt;

import io.smallrye.jwt.build.Jwt;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class JwtUtils {
    public static String generateToken(UUID userId, boolean isAdmin) {
        return Jwt.issuer("ping-backend")
            .subject(userId.toString())
            .groups(isAdmin ? Set.of("admin") : Set.of("user"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .sign();
    }
}

