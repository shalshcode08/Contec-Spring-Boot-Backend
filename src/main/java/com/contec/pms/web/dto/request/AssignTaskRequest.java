package com.contec.pms.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record AssignTaskRequest(
        @NotNull Long assigneeId,
        @NotNull(message = "version is required so concurrent edits are not overwritten") Long version) {
}
