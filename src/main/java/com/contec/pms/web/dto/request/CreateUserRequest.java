package com.contec.pms.web.dto.request;

import com.contec.pms.domain.enums.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password,
        @NotBlank @Size(max = 150) String fullName,
        @NotEmpty(message = "at least one role is required") Set<RoleName> roles) {
}
