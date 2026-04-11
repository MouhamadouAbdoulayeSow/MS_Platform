// Package: sn.lms.platform.shared.security

package edu.projet.MS_Platform.shared.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import edu.projet.MS_Platform.shared.exception.AppErrorCode;
import edu.projet.MS_Platform.shared.exception.AppException;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtil {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.access-expiration}")
    private long accessExpiration;
    
    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;
    
    private SecretKey signingKey;
    
    @PostConstruct
    public void init() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("JWT_SECRET manquant");
        }
        this.signingKey = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret));
    }
    
    public String generateAccessToken(UUID userId, String role, String email) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessExpiration);
        
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("email", email)
                .claim("jti", UUID.randomUUID().toString())
                .issuer("lms-platform")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }
    
    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshExpiration);
        
        return Jwts.builder()
                .subject(userId.toString())
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }
    
    public Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new AppException(AppErrorCode.TOKEN_EXPIRED);
        } catch (JwtException e) {
            throw new AppException(AppErrorCode.TOKEN_INVALID);
        }
    }
    
    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }
    
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }
    
    public String extractJti(String token) {
        return extractAllClaims(token).get("jti", String.class);
    }
    
    public boolean isExpired(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (AppException e) {
            if (e.getErrorCode() == AppErrorCode.TOKEN_EXPIRED) {
                return true;
            }
            throw e;
        }
    }
    
    public long getRemainingTtlMillis(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getExpiration().getTime() - System.currentTimeMillis();
    }
}
