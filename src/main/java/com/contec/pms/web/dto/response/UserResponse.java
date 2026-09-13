package com.contec.pms.web.dto.response;

import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.Role;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        Role role,
        boolean active,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
