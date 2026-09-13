package com.contec.pms.web.dto.request;

import com.contec.pms.domain.enums.ProjectMemberRole;
import jakarta.validation.constraints.NotNull;

public record AddProjectMemberRequest(
        @NotNull Long userId,
        @NotNull ProjectMemberRole projectRole) {
}
