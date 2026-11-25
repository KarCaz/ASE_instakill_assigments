package com.instakill.funnyfy.application;

import org.springframework.http.HttpStatus;

public class FunnyfyException extends RuntimeException {

    private final HttpStatus status;

    public FunnyfyException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
