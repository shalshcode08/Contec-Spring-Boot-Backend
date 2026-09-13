package com.contec.pms.exception;

import org.springframework.http.HttpStatus;

/** The client's {@code version} is behind the stored one — refuse to overwrite. */
public class StaleResourceException extends ApiException {

    public StaleResourceException(String resource, Long expected, Long actual) {
        super(HttpStatus.CONFLICT, "STALE_RESOURCE",
                resource + " has been modified by someone else (submitted version " + expected
                        + ", current version " + actual + "). Reload it and try again.");
    }

    public StaleResourceException(String message) {
        super(HttpStatus.CONFLICT, "STALE_RESOURCE", message);
    }
}
