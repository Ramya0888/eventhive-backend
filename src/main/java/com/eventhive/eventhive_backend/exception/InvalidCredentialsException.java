package com.eventhive.eventhive_backend.exception;

import org.springframework.http.HttpStatus;


public class InvalidCredentialsException extends AppException {
    public InvalidCredentialsException() {
        super("Invalid email or password", HttpStatus.UNAUTHORIZED); // 401
    }
}