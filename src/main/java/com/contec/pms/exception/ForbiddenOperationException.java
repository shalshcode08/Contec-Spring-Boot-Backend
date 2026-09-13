package com.contec.pms.exception;

import org.springframework.http.HttpStatus;

/** The caller is authenticated but not allowed to perform this operation. */
public class ForbiddenOperationException extends ApiException {

    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }
}
