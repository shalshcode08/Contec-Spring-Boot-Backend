package com.contec.pms.web.dto.response;

import com.contec.pms.domain.entity.ProjectMember;
import com.contec.pms.domain.enums.Role;

import java.time.Instant;

public record ProjectMemberResponse(
        Long userId,
        String email,
        String fullName,
        Role role,
        Instant addedAt) {

    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getFullName(),
                member.getUser().getRole(),
                member.getAddedAt());
    }
}
