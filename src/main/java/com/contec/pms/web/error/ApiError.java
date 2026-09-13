package com.contec.pms.web.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "ApiError", description = "Standard error response")
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    @Schema(name = "FieldError")
    public record FieldError(String field, String message) {
    }
}
