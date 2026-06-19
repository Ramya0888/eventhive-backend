package com.eventhive.eventhive_backend.exception;

import org.springframework.http.HttpStatus;


public class UserAlreadyExistsException extends AppException {
    public UserAlreadyExistsException(String email) {
        super("User already exists with email: " + email, HttpStatus.CONFLICT); // 409
    }
}