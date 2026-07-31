package com.eventhive.eventhive_backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;


class JwtUtilTest {

    private JwtUtil jwtUtil;

    // A 64-character test secret — must be at least 256 bits (32 bytes)
    // for HMAC-SHA256 signing, same constraint as production.
    private static final String TEST_SECRET =
            "TestSecretKeyForJwtUnitTestsMustBeAtLeast32CharactersLong123456";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 900_000L);   // 15 min
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 604_800_000L); // 7 days
    }

    @Test
    void generateAccessToken_shouldEncodeCorrectEmail() {
        String token = jwtUtil.generateAccessToken("user@example.com");

        String extractedEmail = jwtUtil.extractEmail(token);

        assertEquals("user@example.com", extractedEmail);
    }

    @Test
    void generateAccessToken_shouldHaveAccessTokenType() {
        String token = jwtUtil.generateAccessToken("user@example.com");

        String tokenType = jwtUtil.extractTokenType(token);

        assertEquals("access", tokenType);
    }

    @Test
    void generateRefreshToken_shouldHaveRefreshTokenType() {
        String token = jwtUtil.generateRefreshToken("user@example.com");

        String tokenType = jwtUtil.extractTokenType(token);

        assertEquals("refresh", tokenType);
    }

    @Test
    void isRefreshToken_shouldReturnTrue_forRefreshToken() {
        String refreshToken = jwtUtil.generateRefreshToken("user@example.com");

        assertTrue(jwtUtil.isRefreshToken(refreshToken));
    }

    @Test
    void isRefreshToken_shouldReturnFalse_forAccessToken() {
        
        String accessToken = jwtUtil.generateAccessToken("user@example.com");

        assertFalse(jwtUtil.isRefreshToken(accessToken));
    }

    @Test
    void validateToken_shouldReturnTrue_whenEmailMatches() {
        String token = jwtUtil.generateAccessToken("user@example.com");

        boolean isValid = jwtUtil.validateToken(token, "user@example.com");

        assertTrue(isValid);
    }

    @Test
    void validateToken_shouldReturnFalse_whenEmailDoesNotMatch() {
        String token = jwtUtil.generateAccessToken("user@example.com");

        boolean isValid = jwtUtil.validateToken(token, "different@example.com");

        assertFalse(isValid);
    }
}