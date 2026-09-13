package com.contec.pms.web.dto.response;

import com.contec.pms.domain.entity.ProjectMember;
import com.contec.pms.domain.enums.ProjectMemberRole;
import com.contec.pms.domain.enums.RoleName;

import java.time.Instant;
import java.util.Set;

public record ProjectMemberResponse(
        Long membershipId,
        Long userId,
        String email,
        String fullName,
        Set<RoleName> globalRoles,
        ProjectMemberRole projectRole,
        Instant addedAt) {

    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getFullName(),
                member.getUser().getRoleNames(),
                member.getProjectRole(),
                member.getAddedAt());
    }
}
