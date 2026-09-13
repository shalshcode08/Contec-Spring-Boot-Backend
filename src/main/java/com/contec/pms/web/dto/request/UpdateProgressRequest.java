package com.contec.pms.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProgressRequest(
        @NotNull @Min(value = 0, message = "progress must be between 0 and 100")
        @Max(value = 100, message = "progress must be between 0 and 100")
        Integer progress,
        @Size(max = 1000) String note,
        @NotNull(message = "version is required so concurrent edits are not overwritten") Long version) {
}
