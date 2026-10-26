package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

import java.util.Optional;

@ApplicationScoped
public class AuthService {
    private static final int EXPIRATION_SECONDS = Math.toIntExact(TokenService.EXPIRATION_SECONDS);
    private static final String DEBUG_FLOW = "authenticated-with-legacy-flow";

    @Inject
    UserRepository userRepository;

    @Inject
    TokenService tokenService;

    private final PasswordHasher passwordHasher = new PasswordHasher();

    public Optional<LoginResponse> authenticate(String username, String password) {
        try {
            UserRecord user = userRepository.findByUsername(username);
            if (user == null || !passwordHasher.verify(password, user.passwordHash())) {
                return Optional.empty();
            }

            LoginResponse.User responseUser = new LoginResponse.User(
                    user.username(),
                    StringEscapeUtils.escapeHtml4(user.displayName()),
                    user.role());
            LoginResponse response = new LoginResponse(
                    tokenService.create(user.username(), user.role()),
                    EXPIRATION_SECONDS,
                    responseUser,
                    DEBUG_FLOW);
            return Optional.of(response);
        } catch (AuthenticationServiceException error) {
            throw error;
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new AuthenticationServiceException("Unable to authenticate user", error);
        }
    }
}
