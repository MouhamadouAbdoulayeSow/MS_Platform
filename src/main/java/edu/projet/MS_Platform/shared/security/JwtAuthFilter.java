// Package: sn.lms.platform.shared.security

package edu.projet.MS_Platform.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import edu.projet.MS_Platform.shared.exception.AppErrorCode;
import edu.projet.MS_Platform.shared.exception.AppException;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    
    private final JwtUtil jwtUtil;
    private final org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String token = authHeader.substring(7);
        
        try {
            var claims = jwtUtil.extractAllClaims(token);
            
            String issuer = claims.getIssuer();
            if (!"lms-platform".equals(issuer)) {
                sendError(response, AppErrorCode.TOKEN_INVALID);
                return;
            }
            
            String jti = jwtUtil.extractJti(token);
            if (Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + jti))) {
                sendError(response, AppErrorCode.TOKEN_REVOKED);
                return;
            }
            
            String userId = jwtUtil.extractUserId(token);
            String role = jwtUtil.extractRole(token);
            
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userId, 
                null, 
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
            
            filterChain.doFilter(request, response);
            
        } catch (AppException e) {
            sendError(response, e.getErrorCode());
        }
    }
    
    private void sendError(HttpServletResponse response, AppErrorCode code) throws IOException {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.valueOf(code.httpStatus), 
            code.userMessage
        );
        problemDetail.setType(URI.create("https://lms-platform/errors/" + code.name().toLowerCase()));
        problemDetail.setTitle("Authentication Error");
        problemDetail.setProperty("errorCode", code.name());
        problemDetail.setProperty("timestamp", Instant.now());
        
        response.setStatus(code.httpStatus);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
    }
}
