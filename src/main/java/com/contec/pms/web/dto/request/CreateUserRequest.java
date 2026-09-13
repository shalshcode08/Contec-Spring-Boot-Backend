package com.contec.pms.web.dto.request;

import com.contec.pms.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        String password,
        @NotBlank @Size(max = 150) String fullName,
        @NotNull Role role) {
}
