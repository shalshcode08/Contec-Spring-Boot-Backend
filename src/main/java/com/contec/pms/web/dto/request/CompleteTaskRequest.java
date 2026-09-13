package com.contec.pms.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompleteTaskRequest(
        @Size(max = 1000) String note,
        @NotNull(message = "version is required so concurrent edits are not overwritten") Long version) {
}
