package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.dto.AuthResponse;
import com.eventhive.eventhive_backend.dto.LoginRequest;
import com.eventhive.eventhive_backend.dto.RegisterRequest;
import com.eventhive.eventhive_backend.entity.Role;
import com.eventhive.eventhive_backend.entity.User;
import com.eventhive.eventhive_backend.exception.InvalidCredentialsException;
import com.eventhive.eventhive_backend.exception.UserAlreadyExistsException;
import com.eventhive.eventhive_backend.repository.RoleRepository;
import com.eventhive.eventhive_backend.repository.UserRepository;
import com.eventhive.eventhive_backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService — registration and login flows.
 *
 * Key security properties verified:
 * 1. Passwords are hashed before saving — never stored as plaintext
 * 2. Duplicate email registration is rejected
 * 3. Wrong password throws InvalidCredentialsException (not a specific
 *    "wrong password" message — prevents email enumeration)
 * 4. Successful login returns valid JWT tokens
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private Role attendeeRole;
    private User savedUser;

    @BeforeEach
    void setUp() {
        // Register request
        registerRequest = new RegisterRequest();
        registerRequest.setName("Test User");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("plainPassword123");
        registerRequest.setRole("ATTENDEE");

        // Login request
        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("plainPassword123");

        // Role
        attendeeRole = new Role();
        attendeeRole.setId(1L);
        attendeeRole.setRoleName("ATTENDEE");

        // Saved user (what the repository returns after save)
        savedUser = User.builder()
                .name("Test User")
                .email("test@example.com")
                .password("$2a$10$hashedPassword")
                .isActive(true)
                .accountLocked(false)
                .failedLoginAttempts(0)
                .roles(Set.of(attendeeRole))
                .build();
        savedUser.setId(1L);
    }

    /**
     * Registration happy path — verifies the critical security property:
     * the password stored in the DB is the ENCODED version, never the
     * plain text the user typed.
     *
     * Interview Q: "How do you verify passwords are hashed?"
     * Answer: "I mock PasswordEncoder to return a known hash, then
     * verify the User saved to the repository has that hash as its
     * password, not the original plain text."
     */
    @Test
    void register_shouldHashPassword_beforeSaving() {
        // Arrange
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByRoleName("ATTENDEE"))
                .thenReturn(Optional.of(attendeeRole));
        when(passwordEncoder.encode("plainPassword123"))
                .thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtil.generateAccessToken(anyString())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-token");

        // Act
        AuthResponse response = authService.register(registerRequest);

        // Assert — verify the User saved to DB had the hashed password
        verify(userRepository).save(argThat(user ->
                "$2a$10$hashedPassword".equals(user.getPassword())
                && !"plainPassword123".equals(user.getPassword())
        ));

        // Response should contain tokens and correct email
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("test@example.com", response.getEmail());
    }

    /**
     * Duplicate email — registering with an already-used email
     * must throw UserAlreadyExistsException immediately.
     *
     * Verifies no user is ever saved when email is duplicate.
     */
    @Test
    void register_shouldThrowException_whenEmailAlreadyExists() {
        // Arrange — email already in DB
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        // Act & Assert
        assertThrows(UserAlreadyExistsException.class,
                () -> authService.register(registerRequest));

        // No user should ever be saved
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    /**
     * Successful login — verifies that correct credentials result in
     * JWT tokens being generated and returned.
     */
    @Test
    void login_shouldReturnTokens_whenCredentialsAreValid() {
        // Arrange — authenticationManager succeeds (no exception thrown)
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(
                        "test@example.com", null, new HashSet<>()));
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(savedUser));
        when(jwtUtil.generateAccessToken("test@example.com"))
                .thenReturn("access-token-abc");
        when(jwtUtil.generateRefreshToken("test@example.com"))
                .thenReturn("refresh-token-abc");

        // Act
        AuthResponse response = authService.login(loginRequest);

        // Assert
        assertEquals("access-token-abc", response.getAccessToken());
        assertEquals("refresh-token-abc", response.getRefreshToken());
        assertEquals("test@example.com", response.getEmail());
        assertTrue(response.getRoles().contains("ATTENDEE"));
    }

    /**
     * Wrong password — verifies that bad credentials throw
     * InvalidCredentialsException with a GENERIC message.
     *
     * The generic message is intentional — a specific "wrong password"
     * vs "email not found" distinction would allow attackers to enumerate
     * valid email addresses (email enumeration attack).
     *
     * Interview Q: "Why not tell the user whether the email or password
     * is wrong?"
     * Answer: "A specific error reveals whether an email exists in our
     * system. An attacker could iterate through emails and use the
     * response to build a list of registered users. A generic
     * 'invalid credentials' message prevents this."
     */
    @Test
    void login_shouldThrowException_whenPasswordIsWrong() {
        // Arrange — authenticationManager throws BadCredentialsException
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // Act & Assert
        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(loginRequest));

        // User should never be loaded or tokens generated on bad credentials
        verify(userRepository, never()).findByEmail(anyString());
        verify(jwtUtil, never()).generateAccessToken(anyString());
    }
}