package com.contec.pms.web.dto.response;

import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.RoleName;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        Set<RoleName> roles,
        boolean active,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRoleNames(), user.isActive(), user.getCreatedAt());
    }
}
