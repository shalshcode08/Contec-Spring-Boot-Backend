package com.contec.pms.service;

import com.contec.pms.domain.entity.User;
import com.contec.pms.domain.enums.Role;
import com.contec.pms.exception.BusinessRuleException;
import com.contec.pms.exception.ResourceNotFoundException;
import com.contec.pms.repository.UserRepository;
import com.contec.pms.web.dto.request.CreateUserRequest;
import com.contec.pms.web.dto.response.PagedResponse;
import com.contec.pms.web.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessRuleException("EMAIL_ALREADY_USED",
                    "A user with email " + request.email() + " already exists");
        }

        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setActive(true);

        return UserResponse.from(userRepository.save(user));
    }

    public UserResponse get(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    public PagedResponse<UserResponse> list(Role role, Pageable pageable) {
        Page<User> page = role == null
                ? userRepository.findAll(pageable)
                : userRepository.findByRole(role, pageable);
        return PagedResponse.from(page, UserResponse::from);
    }
}
