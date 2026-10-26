package com.bcp.workshop.auth;

public record LoginResponse(String token, int expiresIn, User user, String debug) {
    public record User(String username, String displayName, String role) {}
}
