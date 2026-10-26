package com.bcp.workshop.auth;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Optional;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final Logger LOGGER = Logger.getLogger(AuthResource.class);
    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";
    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public Response login(LoginRequest request) {
        Optional<Response> validationError = validateRequest(request);
        if (validationError.isPresent()) {
            return validationError.get();
        }

        try {
            return authService.authenticate(request.username, request.password)
                    .map(loginResponse -> Response.ok(loginResponse).build())
                    .orElseGet(() -> invalidCredentials(request.username));
        } catch (AuthenticationServiceException error) {
            LOGGER.error("Authentication request failed", error);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of(
                    "message", INTERNAL_ERROR_MESSAGE,
                    "exception", INTERNAL_ERROR_CODE
            )).build();
        }
    }

    private Optional<Response> validateRequest(LoginRequest request) {
        if (request == null) {
            return Optional.of(badRequest("Request is null"));
        }
        if (request.username == null || request.username.trim().isEmpty()) {
            return Optional.of(badRequest("Username is required"));
        }
        if (request.password == null || request.password.trim().isEmpty()) {
            return Optional.of(badRequest("Password is required"));
        }
        return Optional.empty();
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("message", message))
                .build();
    }

    private Response invalidCredentials(String username) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("message", "Invalid user " + username + " or password"))
                .build();
    }
}
