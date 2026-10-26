package com.bcp.workshop.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

public final class PasswordHasher {
    private static final String FORMAT_ID = "pbkdf2-sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 310_000;
    private static final int MIN_ITERATIONS = 100_000;
    private static final int MAX_ITERATIONS = 1_000_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BITS = 256;
    private static final int HASH_PART_COUNT = 4;

    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String password) {
        Objects.requireNonNull(password, "password");
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        secureRandom.nextBytes(salt);
        byte[] derivedKey = deriveKey(password, salt, ITERATIONS);

        return String.join("$",
                FORMAT_ID,
                Integer.toString(ITERATIONS),
                Base64.getUrlEncoder().withoutPadding().encodeToString(salt),
                Base64.getUrlEncoder().withoutPadding().encodeToString(derivedKey));
    }

    public boolean verify(String password, String encodedHash) {
        Objects.requireNonNull(password, "password");
        HashParts hashParts = parseHash(encodedHash);
        byte[] candidate = deriveKey(password, hashParts.salt(), hashParts.iterations());
        return MessageDigest.isEqual(candidate, hashParts.derivedKey());
    }

    private HashParts parseHash(String encodedHash) {
        if (encodedHash == null) {
            throw new IllegalArgumentException("Password hash is required");
        }

        String[] parts = encodedHash.split("\\$", -1);
        if (parts.length != HASH_PART_COUNT || !FORMAT_ID.equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported password hash format");
        }

        int iterations;
        byte[] salt;
        byte[] derivedKey;
        try {
            iterations = Integer.parseInt(parts[1]);
            salt = Base64.getUrlDecoder().decode(parts[2]);
            derivedKey = Base64.getUrlDecoder().decode(parts[3]);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Malformed password hash", error);
        }

        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS
                || salt.length != SALT_LENGTH_BYTES
                || derivedKey.length * Byte.SIZE != HASH_LENGTH_BITS) {
            throw new IllegalArgumentException("Invalid password hash parameters");
        }

        return new HashParts(iterations, salt, derivedKey);
    }

    private byte[] deriveKey(String password, byte[] salt, int iterations) {
        char[] passwordCharacters = password.toCharArray();
        PBEKeySpec keySpec = new PBEKeySpec(passwordCharacters, salt, iterations, HASH_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing is unavailable", error);
        } finally {
            keySpec.clearPassword();
            Arrays.fill(passwordCharacters, '\0');
        }
    }

    private record HashParts(int iterations, byte[] salt, byte[] derivedKey) {}
}
