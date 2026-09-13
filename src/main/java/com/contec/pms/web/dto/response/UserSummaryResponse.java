package com.contec.pms.web.dto.response;

import com.contec.pms.domain.entity.User;

public record UserSummaryResponse(Long id, String email, String fullName) {

    public static UserSummaryResponse from(User user) {
        return user == null ? null : new UserSummaryResponse(user.getId(), user.getEmail(), user.getFullName());
    }
}
