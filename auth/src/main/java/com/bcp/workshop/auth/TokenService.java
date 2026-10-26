package com.bcp.workshop.auth;

import io.quarkus.runtime.LaunchMode;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@ApplicationScoped
public class TokenService {
    public static final long EXPIRATION_SECONDS = 3600;
    private static final int SECRET_LENGTH_BYTES = 32;
    private static final String SECRET_KEY_ALGORITHM = "HmacSHA256";

    @ConfigProperty(name = "auth.token.secret")
    Optional<String> configuredSecret;

    private SecretKey signingKey;

    @PostConstruct
    void initialize() {
        String secret = configuredSecret.orElse("");
        byte[] keyBytes = secret.isBlank()
                ? generateDevelopmentKey()
                : decodeConfiguredKey(secret);
        if (keyBytes.length != SECRET_LENGTH_BYTES) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must decode to 32 bytes");
        }
        signingKey = new SecretKeySpec(keyBytes, SECRET_KEY_ALGORITHM);
    }

    public String create(String username, String role) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(EXPIRATION_SECONDS);
        try {
            return Jwt.claims()
                    .claim("sub", username)
                    .claim("role", role)
                    .claim("iat", issuedAt.getEpochSecond())
                    .claim("exp", expiresAt.getEpochSecond())
                    .jws()
                    .algorithm(SignatureAlgorithm.HS256)
                    .sign(signingKey);
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new AuthenticationServiceException("Unable to create signed token", error);
        }
    }

    private byte[] generateDevelopmentKey() {
        if (LaunchMode.current() == LaunchMode.NORMAL) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET is required outside development and tests");
        }
        byte[] keyBytes = new byte[SECRET_LENGTH_BYTES];
        new SecureRandom().nextBytes(keyBytes);
        return keyBytes;
    }

    private byte[] decodeConfiguredKey(String configuredKey) {
        try {
            return Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must be Base64 encoded", error);
        }
    }
}
