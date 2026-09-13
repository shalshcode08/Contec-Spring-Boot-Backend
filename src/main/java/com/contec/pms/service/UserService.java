package com.contec.pms.service;

import com.contec.pms.domain.entity.Role;
import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.RoleName;
import com.contec.pms.exception.BusinessRuleException;
import com.contec.pms.exception.ResourceNotFoundException;
import com.contec.pms.repository.RoleRepository;
import com.contec.pms.repository.UserRepository;
import com.contec.pms.web.dto.request.CreateUserRequest;
import com.contec.pms.web.dto.response.PagedResponse;
import com.contec.pms.web.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessRuleException("EMAIL_ALREADY_USED",
                    "A user with email " + request.email() + " already exists");
        }

        Set<Role> roles = new LinkedHashSet<>();
        for (RoleName roleName : request.roles()) {
            roles.add(roleRepository.findByName(roleName)
                    .orElseThrow(() -> new ResourceNotFoundException("Role " + roleName + " is not configured")));
        }

        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRoles(roles);
        user.setActive(true);

        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse get(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    public PagedResponse<UserResponse> list(RoleName role, Pageable pageable) {
        Page<User> page = role == null
                ? userRepository.findAll(pageable)
                : userRepository.findByRoles_Name(role, pageable);
        return PagedResponse.from(page, UserResponse::from);
    }
}
