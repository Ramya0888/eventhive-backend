package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.dto.AuthResponse;
import com.eventhive.eventhive_backend.dto.LoginRequest;
import com.eventhive.eventhive_backend.dto.RegisterRequest;
import com.eventhive.eventhive_backend.entity.Role;
import com.eventhive.eventhive_backend.entity.User;
import com.eventhive.eventhive_backend.exception.InvalidCredentialsException;
import com.eventhive.eventhive_backend.exception.ResourceNotFoundException;
import com.eventhive.eventhive_backend.exception.UserAlreadyExistsException;
import com.eventhive.eventhive_backend.repository.RoleRepository;
import com.eventhive.eventhive_backend.repository.UserRepository;
import com.eventhive.eventhive_backend.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eventhive.eventhive_backend.dto.RefreshTokenRequest;
import com.eventhive.eventhive_backend.exception.InvalidTokenException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
    }

   
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // 1. Check for duplicate email (lighter EXISTS query)
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(request.getEmail());
        }

        // 2. Fetch the requested role from DB (ORGANIZER or ATTENDEE)
        Role role = roleRepository.findByRoleName(request.getRole())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role not found: " + request.getRole()));

        Set<Role> roles = new HashSet<>();
        roles.add(role);

        // 3. Build the User — password is HASHED before saving (never plaintext!)

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .isActive(true)
                .accountLocked(false)
                .failedLoginAttempts(0)
                .roles(roles)
                .build();

        // 4. Save — Hibernate inserts into users + user_roles (junction)
        User savedUser = userRepository.save(user);
        log.info("User registered successfully with id: {}", savedUser.getId());

        // 5. Generate tokens so the user is logged in immediately after register
        return buildAuthResponse(savedUser);
    }

    /**
     * LOGIN — authenticates email + password, returns tokens.
    
     */
    @Transactional(readOnly = true) 
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        try {
            // 1. Delegate password check to AuthenticationManager.

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            // Generic message — prevents email enumeration (as discussed)
            throw new InvalidCredentialsException();
        }

        // 2. Auth succeeded — load the user to build the response
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        log.info("Login successful for user id: {}", user.getId());

        // 3. Generate fresh tokens
        return buildAuthResponse(user);
    }

    
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // 1. Reject anything that isn't a valid, unexpired refresh token.
        boolean valid;
        try {
            valid = jwtUtil.isRefreshToken(refreshToken);
        } catch (Exception e) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }
        if (!valid) {
            throw new InvalidTokenException("Invalid or expired refresh token");
        }

        // 2. Identify the user from the token's subject (email).
        String email = jwtUtil.extractEmail(refreshToken);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidTokenException("User no longer exists"));

        log.info("Refreshing tokens for user id: {}", user.getId());

        // 3. Issue a fresh token pair.
        return buildAuthResponse(user);
    }

   
    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.toSet());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .roles(roleNames)
                .build();
    }
}