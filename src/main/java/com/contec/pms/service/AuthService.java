package com.contec.pms.service;

import com.contec.pms.domain.entity.User;
import com.contec.pms.exception.ResourceNotFoundException;
import com.contec.pms.repository.UserRepository;
import com.contec.pms.security.AppUserDetails;
import com.contec.pms.security.JwtService;
import com.contec.pms.web.dto.request.LoginRequest;
import com.contec.pms.web.dto.response.LoginResponse;
import com.contec.pms.web.dto.response.UserResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        if (!principal.isEnabled()) {
            throw new DisabledException("Account is disabled");
        }

        String token = jwtService.generateToken(principal);
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));

        return LoginResponse.of(token, jwtService.getExpirationSeconds(), UserResponse.from(user));
    }

    public UserResponse currentUser(AppUserDetails principal) {
        return userRepository.findById(principal.getId())
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));
    }
}
