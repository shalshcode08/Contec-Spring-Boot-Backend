package com.contec.pms.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record AddProjectMemberRequest(@NotNull Long userId) {
}
