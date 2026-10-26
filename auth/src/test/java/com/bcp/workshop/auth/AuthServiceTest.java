package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void authenticatesUserAndMapsEscapedResponse() {
        UserRecord user = userRecord("student", "<Student & Co>", "STUDENT");
        AuthService authService = authServiceFor(user);

        Optional<LoginResponse> response = authService.authenticate("student", "Lab-only-Password-47");

        assertTrue(response.isPresent());
        assertEquals("student", response.get().user().username());
        assertEquals("&lt;Student &amp; Co&gt;", response.get().user().displayName());
        assertEquals("STUDENT", response.get().user().role());
        assertEquals(3600, response.get().expiresIn());
        assertTrue(response.get().token().length() > 0);
    }

    @Test
    void rejectsIncorrectPassword() {
        AuthService authService = authServiceFor(userRecord("student", "Student", "STUDENT"));

        assertFalse(authService.authenticate("student", "incorrect").isPresent());
    }

    @Test
    void rejectsUnknownUsername() {
        AuthService authService = authServiceFor(null);

        assertFalse(authService.authenticate("unknown", "incorrect").isPresent());
    }

    @Test
    void preservesAuthenticationServiceExceptions() {
        AuthenticationServiceException expected =
                new AuthenticationServiceException("repository failure", new IllegalStateException());
        AuthService authService = authServiceWithRepository(new UserRepository() {
            @Override
            public UserRecord findByUsername(String username) {
                throw expected;
            }
        });

        AuthenticationServiceException actual = assertThrows(
                AuthenticationServiceException.class,
                () -> authService.authenticate("student", "Lab-only-Password-47"));

        assertSame(expected, actual);
    }

    @Test
    void wrapsUnexpectedAuthenticationFailures() {
        IllegalStateException cause = new IllegalStateException("database unavailable");
        AuthService authService = authServiceWithRepository(new UserRepository() {
            @Override
            public UserRecord findByUsername(String username) {
                throw cause;
            }
        });

        AuthenticationServiceException actual = assertThrows(
                AuthenticationServiceException.class,
                () -> authService.authenticate("student", "Lab-only-Password-47"));

        assertEquals("Unable to authenticate user", actual.getMessage());
        assertSame(cause, actual.getCause());
    }

    private UserRecord userRecord(String username, String displayName, String role) {
        return new UserRecord(1, username, passwordHasher.hash("Lab-only-Password-47"), displayName, role);
    }

    private AuthService authServiceFor(UserRecord user) {
        UserRepository repository = new UserRepository() {
            @Override
            public UserRecord findByUsername(String username) {
                return user;
            }
        };
        return authServiceWithRepository(repository);
    }

    private AuthService authServiceWithRepository(UserRepository repository) {
        AuthService authService = new AuthService();
        authService.userRepository = repository;
        TokenService tokenService = new TokenService();
        tokenService.configuredSecret = Optional.of(Base64.getEncoder().encodeToString(new byte[32]));
        tokenService.initialize();
        authService.tokenService = tokenService;
        return authService;
    }
}
