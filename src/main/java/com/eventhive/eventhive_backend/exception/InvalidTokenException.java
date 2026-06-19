package com.eventhive.eventhive_backend.exception;

import org.springframework.http.HttpStatus;


public class InvalidTokenException extends AppException {
    public InvalidTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED); // 401
    }
}