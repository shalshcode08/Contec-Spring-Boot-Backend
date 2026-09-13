package com.contec.pms.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors that map directly onto an HTTP response. Every subclass
 * carries a stable machine-readable {@code code} alongside the status.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
