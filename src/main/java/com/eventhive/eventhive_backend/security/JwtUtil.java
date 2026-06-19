package com.eventhive.eventhive_backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

/**
 * JwtUtil — handles JWT creation, parsing, and validation.
 *
 * Each token carries a "type" claim ("access" or "refresh") so a token
 * can only be used for its intended purpose — e.g. an access token cannot
 * be replayed against the refresh endpoint to mint new tokens.
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // Token type constants — avoids magic strings scattered in code.
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ========================================================
    // TOKEN GENERATION
    // ========================================================

    public String generateAccessToken(String email) {
        return buildToken(email, accessTokenExpiration, ACCESS_TOKEN_TYPE);
    }

    public String generateRefreshToken(String email) {
        return buildToken(email, refreshTokenExpiration, REFRESH_TOKEN_TYPE);
    }

    /**
     * Builds a signed JWT with subject (email), issued/expiry timestamps,
     * and a custom "type" claim marking it as access or refresh.
     */
    private String buildToken(String email, long expiration, String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(email)
                .claim(TOKEN_TYPE_CLAIM, tokenType)   // the purpose-scoping claim
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    // ========================================================
    // TOKEN PARSING / VALIDATION
    // ========================================================

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Reads the custom "type" claim from the token.
     */
    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Validates an ACCESS token against a given email.
     * Used by the JWT filter on every request.
     */
    public boolean validateToken(String token, String email) {
        final String tokenEmail = extractEmail(token);
        return tokenEmail.equals(email) && !isTokenExpired(token);
    }

    /**
     * Validates that a token is specifically a REFRESH token and not expired.
     *
     * Interview Q: "How do you stop an access token being used to refresh?"
     * Answer: Each token carries a type claim; the refresh endpoint accepts
     * only tokens whose type is "refresh". An access token sent here fails this check.
     */
    public boolean isRefreshToken(String token) {
        return REFRESH_TOKEN_TYPE.equals(extractTokenType(token)) && !isTokenExpired(token);
    }
}