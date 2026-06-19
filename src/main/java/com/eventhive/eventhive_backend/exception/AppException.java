package com.eventhive.eventhive_backend.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;


@Getter
public class AppException extends RuntimeException {

    private final HttpStatus httpStatus;

    public AppException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }
}