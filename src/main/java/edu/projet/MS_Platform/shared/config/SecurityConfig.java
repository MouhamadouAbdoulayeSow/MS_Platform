// Package: sn.lms.platform.shared.config

package edu.projet.MS_Platform.shared.config;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import edu.projet.MS_Platform.shared.exception.AppErrorCode;
import edu.projet.MS_Platform.shared.security.JwtAuthFilter;
import edu.projet.MS_Platform.shared.security.JwtUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;
    
    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil, 
                                       org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate,
                                       ObjectMapper objectMapper) {
        return new JwtAuthFilter(jwtUtil, redisTemplate, objectMapper);
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/refresh", 
                                "/auth/forgot-password", "/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint(objectMapper))
                .accessDeniedHandler(accessDeniedHandler(objectMapper))
            )
            .build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (HttpServletRequest request, HttpServletResponse response, 
                org.springframework.security.core.AuthenticationException authException) -> {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, 
                "Authentification requise"
            );
            problemDetail.setType(URI.create("https://lms-platform/errors/unauthorized"));
            problemDetail.setTitle("Authentication Failed");
            problemDetail.setProperty("errorCode", "UNAUTHORIZED");
            problemDetail.setProperty("timestamp", Instant.now());
            
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            try {
                response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
            } catch (IOException e) {
                // Log error but don't expose it
            }
        };
    }
    
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (HttpServletRequest request, HttpServletResponse response, 
                org.springframework.security.access.AccessDeniedException accessDeniedException) -> {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, 
                AppErrorCode.FORBIDDEN.userMessage
            );
            problemDetail.setType(URI.create("https://lms-platform/errors/forbidden"));
            problemDetail.setTitle("Access Denied");
            problemDetail.setProperty("errorCode", AppErrorCode.FORBIDDEN.name());
            problemDetail.setProperty("timestamp", Instant.now());
            
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            try {
                response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
            } catch (IOException e) {
                // Log error but don't expose it
            }
        };
    }
}
