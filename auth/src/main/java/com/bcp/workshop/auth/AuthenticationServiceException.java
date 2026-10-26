package com.bcp.workshop.auth;

public class AuthenticationServiceException extends RuntimeException {
    public AuthenticationServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
