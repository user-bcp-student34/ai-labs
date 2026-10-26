package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {
    @Test
    void reportsHealthAsUp() {
        given()
                .accept("application/json")
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("status", is("UP"));
    }

    @Test
    void reportsNoHealthChecksWhenNoneAreRegistered() {
        given()
                .accept("application/json")
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("checks", hasSize(0));
    }

    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"))
                .body("token", notNullValue());
    }

    @Test
    void logsInWithoutOptionalClientField() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("user.username", is("student"))
                .body("token", notNullValue());
    }

    @Test
    void rejectsNullRequest() {
        given()
                .contentType("application/json")
                .body("null")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMissingUsername() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsBlankUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"  \",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsBlankPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\" \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"incorrect\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("message", is("Invalid user student or password"));
    }

    @Test
    void treatsQuoteInPasswordAsInvalidCredentials() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"not'valid\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    void rejectsUnconfiguredOriginInCorsResponse() {
        given()
                .header("Origin", "https://unconfigured.example")
                .contentType("application/json")
                .body("{\"username\":\"unknown\",\"password\":\"incorrect\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(403)
                .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    void returnsGenericErrorWhenAuthenticationServiceFailsOverHttp() {
        QuarkusMock.installMockForType(new AuthService() {
            @Override
            public Optional<LoginResponse> authenticate(String username, String password) {
                throw new AuthenticationServiceException("sensitive internal detail", new IllegalStateException());
            }
        }, AuthService.class);

        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(500)
                .body("message", is("Internal server error"))
                .body("exception", is("INTERNAL_ERROR"));
    }

    @Test
    void allowsDocumentedLocalFrontendOrigin() {
        given()
                .header("Origin", "http://localhost:4200")
                .contentType("application/json")
                .body("{\"username\":\"unknown\",\"password\":\"incorrect\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .header("Access-Control-Allow-Origin", is("http://localhost:4200"));
    }

    @Test
    void storesSeedPasswordAsHashInDatabase() throws Exception {
        String databaseUrl = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT password_hash FROM legacy_users WHERE username = ?")) {
            statement.setString(1, "student");
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                String passwordHash = result.getString("password_hash");
                assertNotEquals("Workshop123!", passwordHash);
                assertTrue(new PasswordHasher().verify("Workshop123!", passwordHash));
            }
        }
    }

    @Test
    void exposesUnexpectedExceptionDetailsInObservedErrorResponse() {
        AuthResource resource = resourceWithService(new AuthService() {
            @Override
            public Optional<LoginResponse> authenticate(String username, String password) {
                throw new AuthenticationServiceException("internal detail", new IllegalStateException());
            }
        });
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";

        Response response = resource.login(request);
        Map<?, ?> body = (Map<?, ?>) response.getEntity();

        assertEquals(500, response.getStatus());
        assertEquals("Internal server error", body.get("message"));
        assertEquals("INTERNAL_ERROR", body.get("exception"));
    }

    @Test
    void escapesDisplayNameInObservedSuccessResponse() {
        AuthResource resource = resourceWithService(new AuthService() {
            @Override
            public Optional<LoginResponse> authenticate(String username, String password) {
                return Optional.of(new LoginResponse(
                        "test-token",
                        3600,
                        new LoginResponse.User(username, "&lt;Student &amp; Co&gt;", "STUDENT"),
                        "authenticated-with-legacy-flow"));
            }
        });
        LoginRequest request = new LoginRequest();
        request.username = "student";
        request.password = "Workshop123!";

        Response response = resource.login(request);
        LoginResponse body = (LoginResponse) response.getEntity();

        assertEquals(200, response.getStatus());
        assertEquals("&lt;Student &amp; Co&gt;", body.user().displayName());
        assertNotNull(body.token());
    }

    private static AuthResource resourceWithService(AuthService authService) {
        AuthResource resource = new AuthResource();
        resource.authService = authService;
        return resource;
    }
}
