package com.eventhive.eventhive_backend.exception;

import org.springframework.http.HttpStatus;

public class InvalidEventStateException extends AppException {
    public InvalidEventStateException(String message) {
        super(message, HttpStatus.CONFLICT); // 409
    }
}