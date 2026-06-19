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


@Component
public class JwtUtil {

    // Injected from application.properties
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

   
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

  
    // TOKEN GENERATION
   

    public String generateAccessToken(String email) {
        return buildToken(email, accessTokenExpiration);
    }

   
    public String generateRefreshToken(String email) {
        return buildToken(email, refreshTokenExpiration);
    }

 
    private String buildToken(String email, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(email)                  // who the token belongs to
                .issuedAt(now)                   // when it was created
                .expiration(expiryDate)          // when it expires
                .signWith(getSigningKey())       // sign with secret (HS256)
                .compact();                      // serialize to compact string
    }

    
    // TOKEN PARSING / VALIDATION
   

   
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

  
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())     // verify signature with our secret
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

   
    public boolean validateToken(String token, String email) {
        final String tokenEmail = extractEmail(token);
        return tokenEmail.equals(email) && !isTokenExpired(token);
    }
}