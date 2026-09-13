package com.contec.pms.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RejectTaskRequest(
        @NotBlank(message = "a rejection reason is required") @Size(max = 1000) String reason,
        @NotNull(message = "version is required so concurrent edits are not overwritten") Long version) {
}
