// Package: edu.projet.MS_Platform.auth.controller
package edu.projet.MS_Platform.auth.controller;

import tools.jackson.databind.ObjectMapper;
import edu.projet.MS_Platform.auth.dto.*;
import edu.projet.MS_Platform.auth.entity.Role;
import edu.projet.MS_Platform.auth.service.AuthService;
import edu.projet.MS_Platform.shared.exception.AppException;
import edu.projet.MS_Platform.shared.exception.AppErrorCode;
import edu.projet.MS_Platform.shared.config.SecurityConfig;
import edu.projet.MS_Platform.shared.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests: the real SecurityConfig and JwtAuthFilter are loaded,
 * the service, JWT utility and Redis are mocked.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String VALID_TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID testUserId;
    private AuthResponse testAuthResponse;
    private UserProfileResponse testUserProfile;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUserProfile = new UserProfileResponse(
                testUserId, "Test User", "test@example.com", Role.ETUDIANT,
                "L1", "http://avatar.com", true, LocalDateTime.now(), LocalDateTime.now()
        );
        testAuthResponse = new AuthResponse(
                "accessToken", "refreshToken", "Bearer", 900, testUserProfile
        );

        // Any Bearer token is accepted by the JWT filter as the test user
        Claims claims = mock(Claims.class);
        when(claims.getIssuer()).thenReturn("lms-platform");
        when(jwtUtil.extractAllClaims(anyString())).thenReturn(claims);
        when(jwtUtil.extractJti(anyString())).thenReturn("jti");
        when(jwtUtil.extractUserId(anyString())).thenReturn(testUserId.toString());
        when(jwtUtil.extractRole(anyString())).thenReturn("ETUDIANT");
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
    }

    @Test
    void register_validRequest_returns201() throws Exception {
        // Given
        RegisterRequest req = new RegisterRequest("Test User", "test@example.com", "Password123!", Role.ETUDIANT);
        when(authService.register(any(RegisterRequest.class))).thenReturn(testAuthResponse);

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("accessToken"))
                .andExpect(jsonPath("$.refreshToken").value("refreshToken"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(testUserId.toString()));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void register_passwordWithoutUppercase_returns400() throws Exception {
        // Given
        RegisterRequest req = new RegisterRequest("Test User", "test@example.com", "password123!", Role.ETUDIANT);

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.motDePasse").exists());

        verify(authService, never()).register(any());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        // Given
        RegisterRequest req = new RegisterRequest("Test User", "invalid-email", "Password123!", Role.ETUDIANT);

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void register_emptyName_returns400() throws Exception {
        // Given
        RegisterRequest req = new RegisterRequest("", "test@example.com", "Password123!", Role.ETUDIANT);

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void login_validRequest_returns200() throws Exception {
        // Given
        LoginRequest req = new LoginRequest("test@example.com", "Password123!");
        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(testAuthResponse);

        // When & Then
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("accessToken"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));

        verify(authService).login(any(LoginRequest.class), anyString());
    }

    @Test
    void login_tooManyRequests_returns429() throws Exception {
        // Given
        LoginRequest req = new LoginRequest("test@example.com", "Password123!");
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new AppException(AppErrorCode.TOO_MANY_REQUESTS, "Too many requests"));

        // When & Then
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests());

        verify(authService).login(any(LoginRequest.class), anyString());
    }

    @Test
    void forgotPassword_emailExists_returns200() throws Exception {
        // Given
        ForgotPasswordRequest req = new ForgotPasswordRequest("test@example.com");
        doNothing().when(authService).forgotPassword("test@example.com");

        // When & Then
        mockMvc.perform(post("/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Si l'email existe, un lien a été envoyé."));

        verify(authService).forgotPassword("test@example.com");
    }

    @Test
    void forgotPassword_emailNotFound_returns200() throws Exception {
        // Given
        ForgotPasswordRequest req = new ForgotPasswordRequest("nonexistent@example.com");
        doThrow(new AppException(AppErrorCode.USER_NOT_FOUND, "User not found"))
                .when(authService).forgotPassword("nonexistent@example.com");

        // When & Then - Même réponse 200 pour ne pas révéler l'existence de l'email
        mockMvc.perform(post("/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Si l'email existe, un lien a été envoyé."));

        verify(authService).forgotPassword("nonexistent@example.com");
    }

    @Test
    void resetPassword_validToken_returns200() throws Exception {
        // Given
        ResetPasswordRequest req = new ResetPasswordRequest("valid-token", "NewPassword123!");
        doNothing().when(authService).resetPassword(req);

        // When & Then
        mockMvc.perform(post("/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mot de passe réinitialisé."));

        verify(authService).resetPassword(req);
    }

    @Test
    void refresh_validToken_returns200() throws Exception {
        // Given
        RefreshRequest req = new RefreshRequest("valid-refresh-token");
        when(authService.refresh("valid-refresh-token")).thenReturn(testAuthResponse);

        // When & Then
        mockMvc.perform(post("/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("accessToken"))
                .andExpect(jsonPath("$.refreshToken").value("refreshToken"));

        verify(authService).refresh("valid-refresh-token");
    }

    @Test
    void getMe_withoutToken_returns401() throws Exception {
        // When & Then
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).getMe(any());
    }

    @Test
    void getMe_withToken_returns200() throws Exception {
        // Given
        when(authService.getMe(any())).thenReturn(testUserProfile);

        // When & Then
        mockMvc.perform(get("/auth/me")
                        .with(csrf())
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUserId.toString()))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(authService).getMe(testUserId);
    }

    @Test
    void getMe_revokedToken_returns401() throws Exception {
        // Given
        when(redisTemplate.hasKey("blacklist:jti")).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REVOKED"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(authService, never()).getMe(any());
    }

    @Test
    void updateMe_withAuthenticatedUser_returns200() throws Exception {
        // Given
        UpdateProfileRequest req = new UpdateProfileRequest("Updated Name", "http://new-avatar.com", "L2");
        UserProfileResponse updatedProfile = new UserProfileResponse(
                testUserId, "Updated Name", "test@example.com", Role.ETUDIANT,
                "L2", "http://new-avatar.com", true, LocalDateTime.now(), LocalDateTime.now()
        );
        when(authService.updateProfile(any(), any(UpdateProfileRequest.class))).thenReturn(updatedProfile);

        // When & Then
        mockMvc.perform(put("/auth/me")
                        .with(csrf())
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Updated Name"))
                .andExpect(jsonPath("$.avatar").value("http://new-avatar.com"))
                .andExpect(jsonPath("$.niveau").value("L2"));

        verify(authService).updateProfile(any(), any(UpdateProfileRequest.class));
    }

    @Test
    void changePassword_withAuthenticatedUser_returns204() throws Exception {
        // Given
        ChangePasswordRequest req = new ChangePasswordRequest("CurrentPassword123!", "NewPassword123!");
        doNothing().when(authService).changePassword(any(), any(ChangePasswordRequest.class));

        // When & Then
        mockMvc.perform(put("/auth/me/password")
                        .with(csrf())
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(authService).changePassword(any(), any(ChangePasswordRequest.class));
    }

    @Test
    void logout_withAuthenticatedUser_returns204() throws Exception {
        // Given
        RefreshRequest req = new RefreshRequest("refresh-token");
        doNothing().when(authService).logout(anyString(), anyString());

        // When & Then
        mockMvc.perform(post("/auth/logout")
                        .with(csrf())
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(authService).logout(eq("access-token"), eq("refresh-token"));
    }

    @Test
    void register_invalidJson_returns400() throws Exception {
        // When & Then
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void login_missingFields_returns400() throws Exception {
        // Given
        LoginRequest req = new LoginRequest("", ""); // Champs vides

        // When & Then
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any(), any());
    }

    @Test
    void resetPassword_shortPassword_returns400() throws Exception {
        // Given
        ResetPasswordRequest req = new ResetPasswordRequest("token", "short"); // Moins de 8 caractères

        // When & Then
        mockMvc.perform(post("/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).resetPassword(any());
    }
}
