package com.bcp.workshop.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {
    private final byte[] testSecret = new byte[32];
    private final ObjectMapper objectMapper = new ObjectMapper();
    private TokenService tokenService;

    @BeforeEach
    void createTokenService() {
        Arrays.fill(testSecret, (byte) 0x41);
        tokenService = new TokenService();
        tokenService.configuredSecret = Optional.of(Base64.getEncoder().encodeToString(testSecret));
        tokenService.initialize();
    }

    @Test
    void createsSignedTokenWithSubjectRoleAndExpiry() throws Exception {
        String token = tokenService.create("student", "STUDENT");
        String[] segments = token.split("\\.");
        assertEquals(3, segments.length);

        JsonNode header = objectMapper.readTree(decode(segments[0]));
        JsonNode claims = objectMapper.readTree(decode(segments[1]));
        assertEquals("HS256", header.path("alg").asText());
        assertEquals("student", claims.path("sub").asText());
        assertEquals("STUDENT", claims.path("role").asText());
        assertEquals(TokenService.EXPIRATION_SECONDS,
                claims.path("exp").asLong() - claims.path("iat").asLong());

        Mac signer = Mac.getInstance("HmacSHA256");
        signer.init(new SecretKeySpec(testSecret, "HmacSHA256"));
        byte[] expectedSignature = signer.doFinal(
                (segments[0] + "." + segments[1]).getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals(expectedSignature, Base64.getUrlDecoder().decode(segments[2]));
        assertTrue(claims.path("exp").asLong() > claims.path("iat").asLong());
    }

    @Test
    void rejectsConfiguredSecretWithInvalidEncoding() {
        TokenService service = new TokenService();
        service.configuredSecret = Optional.of("invalid-base64");

        IllegalStateException error = assertThrows(IllegalStateException.class, service::initialize);

        assertEquals("AUTH_TOKEN_SECRET must be Base64 encoded", error.getMessage());
    }

    @Test
    void rejectsConfiguredSecretWithInvalidLength() {
        TokenService service = new TokenService();
        service.configuredSecret = Optional.of(Base64.getEncoder().encodeToString(new byte[31]));

        IllegalStateException error = assertThrows(IllegalStateException.class, service::initialize);

        assertEquals("AUTH_TOKEN_SECRET must decode to 32 bytes", error.getMessage());
    }

    private String decode(String tokenSegment) {
        return new String(Base64.getUrlDecoder().decode(tokenSegment), StandardCharsets.UTF_8);
    }
}
