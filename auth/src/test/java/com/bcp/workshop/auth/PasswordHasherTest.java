package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void hashesPasswordWithUniqueSaltAndVerifiesIt() {
        String password = "Lab-only-Password-47";

        String firstHash = passwordHasher.hash(password);
        String secondHash = passwordHasher.hash(password);

        assertNotEquals(password, firstHash);
        assertNotEquals(firstHash, secondHash);
        assertTrue(passwordHasher.verify(password, firstHash));
        assertFalse(passwordHasher.verify("incorrect", firstHash));
    }

    @Test
    void rejectsUnsupportedAndMalformedHashes() {
        assertThrows(IllegalArgumentException.class, () -> passwordHasher.verify("password", null));
        assertThrows(IllegalArgumentException.class,
                () -> passwordHasher.verify("password", "unknown$310000$c2FsdA$aGFzaA"));
        assertThrows(IllegalArgumentException.class,
                () -> passwordHasher.verify("password", "pbkdf2-sha256$invalid$c2FsdA$aGFzaA"));
    }

    @Test
    void rejectsHashesWithInvalidParameters() {
        String validSalt = encode(new byte[16]);
        String validDerivedKey = encode(new byte[32]);
        String shortSalt = encode(new byte[8]);
        String shortDerivedKey = encode(new byte[16]);

        assertThrows(IllegalArgumentException.class,
                () -> passwordHasher.verify("password", encodedHash(99_999, validSalt, validDerivedKey)));
        assertThrows(IllegalArgumentException.class,
                () -> passwordHasher.verify("password", encodedHash(310_000, shortSalt, validDerivedKey)));
        assertThrows(IllegalArgumentException.class,
                () -> passwordHasher.verify("password", encodedHash(310_000, validSalt, shortDerivedKey)));
    }

    private String encodedHash(int iterations, String salt, String derivedKey) {
        return "pbkdf2-sha256$" + iterations + "$" + salt + "$" + derivedKey;
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
