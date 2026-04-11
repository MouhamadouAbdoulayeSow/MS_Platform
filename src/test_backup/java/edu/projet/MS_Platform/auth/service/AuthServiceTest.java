// Package: edu.projet.MS_Platform.auth.service
package edu.projet.MS_Platform.auth.service;

import edu.projet.MS_Platform.auth.dto.*;
import edu.projet.MS_Platform.auth.entity.Role;
import edu.projet.MS_Platform.auth.entity.User;
import edu.projet.MS_Platform.auth.mapper.UserMapper;
import edu.projet.MS_Platform.auth.repository.UserRepository;
import edu.projet.MS_Platform.shared.exception.AppException;
import edu.projet.MS_Platform.shared.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static edu.projet.MS_Platform.shared.exception.AppErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private UserMapper userMapper;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private UUID testUserId;
    private String testEmail;
    private String testPassword;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testEmail = "test@example.com";
        testPassword = "Password123!";
        
        testUser = User.builder()
                .id(testUserId)
                .nom("Test User")
                .email(testEmail)
                .motDePasse("hashedPassword")
                .role(Role.ETUDIANT)
                .actif(true)
                .locked(false)
                .failedAttempts(0)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void register_success() {
        // Given
        RegisterRequest req = new RegisterRequest("Test User", testEmail, testPassword, Role.ETUDIANT);
        when(userRepository.existsByEmail(testEmail)).thenReturn(false);
        when(passwordEncoder.encode(testPassword)).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtUtil.generateAccessToken(any(UUID.class), anyString(), anyString())).thenReturn("accessToken");
        when(jwtUtil.generateRefreshToken(any(UUID.class))).thenReturn("refreshToken");
        when(jwtUtil.extractJti(anyString())).thenReturn("jti");
        when(userMapper.toProfileResponse(any(User.class))).thenReturn(
                new UserProfileResponse(testUserId, "Test User", testEmail, Role.ETUDIANT, null, null, true, null, null)
        );

        // When
        AuthResponse response = authService.register(req);

        // Then
        assertNotNull(response);
        verify(userRepository).save(any(User.class));
        verify(passwordEncoder).encode(testPassword);
        verify(redisTemplate.opsForValue()).set(eq("refresh:jti"), eq(testUserId.toString()), eq(7L), any());
    }

    @Test
    void register_emailAlreadyExists() {
        // Given
        RegisterRequest req = new RegisterRequest("Test User", testEmail, testPassword, Role.ETUDIANT);
        when(userRepository.existsByEmail(testEmail)).thenReturn(true);

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> authService.register(req));
        assertEquals(USER_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void register_roleAdminBlocked() {
        // Given
        RegisterRequest req = new RegisterRequest("Test User", testEmail, testPassword, Role.ADMIN);

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> authService.register(req));
        assertEquals(FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void login_success() {
        // Given
        LoginRequest req = new LoginRequest(testEmail, testPassword);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, "hashedPassword")).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtUtil.generateAccessToken(any(UUID.class), anyString(), anyString())).thenReturn("accessToken");
        when(jwtUtil.generateRefreshToken(any(UUID.class))).thenReturn("refreshToken");
        when(jwtUtil.extractJti(anyString())).thenReturn("jti");
        when(userMapper.toProfileResponse(any(User.class))).thenReturn(
                new UserProfileResponse(testUserId, "Test User", testEmail, Role.ETUDIANT, null, null, true, null, null)
        );

        // When
        AuthResponse response = authService.login(req, "127.0.0.1");

        // Then
        assertNotNull(response);
        assertEquals(0, testUser.getFailedAttempts());
        verify(redisTemplate).delete("rate:login:127.0.0.1");
        verify(redisTemplate).delete("lockout:" + testEmail);
    }

    @Test
    void login_wrongPassword() {
        // Given
        LoginRequest req = new LoginRequest(testEmail, "wrongPassword");
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "hashedPassword")).thenReturn(false);
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> authService.login(req, "127.0.0.1"));
        assertEquals(INVALID_CREDENTIALS, exception.getErrorCode());
        verify(testUser).setFailedAttempts(1);
    }

    @Test
    void login_accountLocked() {
        // Given
        testUser.setLocked(true);
        LoginRequest req = new LoginRequest(testEmail, testPassword);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, "hashedPassword")).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(1L);

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> authService.login(req, "127.0.0.1"));
        assertEquals(ACCOUNT_LOCKED, exception.getErrorCode());
    }

    @Test
    void login_rateLimitByIp() {
        // Given
        LoginRequest req = new LoginRequest(testEmail, testPassword);
        when(valueOps.increment("rate:login:127.0.0.1")).thenReturn(6L);

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> authService.login(req, "127.0.0.1"));
        assertEquals(TOO_MANY_REQUESTS, exception.getErrorCode());
    }

    @Test
    void login_timingAttack() {
        // Given
        LoginRequest req = new LoginRequest("nonexistent@example.com", testPassword);
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());
        when(valueOps.increment(anyString())).thenReturn(1L);

        // When
        assertThrows(AppException.class, () -> authService.login(req, "127.0.0.1"));

        // Then - Vérifier que BCrypt est appelé même si l'utilisateur n'existe pas
        verify(passwordEncoder).matches(eq(testPassword), anyString()); // DUMMY_HASH
    }

    @Test
    void refresh_success() {
        // Given
        String oldRefreshToken = "oldRefreshToken";
        String oldJti = "oldJti";
        String newAccessToken = "newAccessToken";
        String newRefreshToken = "newRefreshToken";
        String newJti = "newJti";

        when(jwtUtil.extractJti(oldRefreshToken)).thenReturn(oldJti);
        when(jwtUtil.extractUserId(oldRefreshToken)).thenReturn(testUserId.toString());
        when(valueOps.get("refresh:" + oldJti)).thenReturn(testUserId.toString());
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(jwtUtil.getRemainingTtlMillis(oldRefreshToken)).thenReturn(1000L);
        when(jwtUtil.generateAccessToken(any(UUID.class), anyString(), anyString())).thenReturn(newAccessToken);
        when(jwtUtil.generateRefreshToken(any(UUID.class))).thenReturn(newRefreshToken);
        when(jwtUtil.extractJti(newRefreshToken)).thenReturn(newJti);
        when(userMapper.toProfileResponse(any(User.class))).thenReturn(
                new UserProfileResponse(testUserId, "Test User", testEmail, Role.ETUDIANT, null, null, true, null, null)
        );

        // When
        AuthResponse response = authService.refresh(oldRefreshToken);

        // Then
        assertNotNull(response);
        assertEquals(newAccessToken, response.accessToken());
        assertEquals(newRefreshToken, response.refreshToken());
        verify(redisTemplate.opsForValue()).set("blacklist:" + oldJti, "1", 1000L, TimeUnit.MILLISECONDS);
        verify(redisTemplate).delete("refresh:" + oldJti);
        verify(redisTemplate.opsForValue()).set("refresh:" + newJti, testUserId.toString(), 7L, TimeUnit.DAYS);
    }

    @Test
    void refresh_tokenNotInRedis() {
        // Given
        String refreshToken = "refreshToken";
        String jti = "jti";

        when(jwtUtil.extractJti(refreshToken)).thenReturn(jti);
        when(jwtUtil.extractUserId(refreshToken)).thenReturn(testUserId.toString());
        when(valueOps.get("refresh:" + jti)).thenReturn(null);

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> authService.refresh(refreshToken));
        assertEquals(TOKEN_REVOKED, exception.getErrorCode());
    }

    @Test
    void logout_blacklistsBothTokens() {
        // Given
        String accessToken = "accessToken";
        String refreshToken = "refreshToken";
        String accessJti = "accessJti";
        String refreshJti = "refreshJti";

        when(jwtUtil.extractJti(accessToken)).thenReturn(accessJti);
        when(jwtUtil.extractJti(refreshToken)).thenReturn(refreshJti);
        when(jwtUtil.getRemainingTtlMillis(accessToken)).thenReturn(1000L);

        // When
        authService.logout(accessToken, refreshToken);

        // Then
        verify(redisTemplate.opsForValue()).set("blacklist:" + accessJti, "1", 1000L, TimeUnit.MILLISECONDS);
        verify(redisTemplate.opsForValue()).set("blacklist:" + refreshJti, "1", 7L, TimeUnit.DAYS);
        verify(redisTemplate).delete("refresh:" + refreshJti);
    }

    @Test
    void forgotPassword_emailNotFound() {
        // Given
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        // When & Then - Aucune exception ne doit être levée
        assertDoesNotThrow(() -> authService.forgotPassword(testEmail));
        verify(mailSender, never()).send(any());
    }

    @Test
    void resetPassword_success() {
        // Given
        String token = "resetToken";
        ResetPasswordRequest req = new ResetPasswordRequest(token, "NewPassword123!");
        
        when(valueOps.get("reset:" + token)).thenReturn(testUserId.toString());
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("newHashedPassword");

        // When
        assertDoesNotThrow(() -> authService.resetPassword(req));

        // Then
        verify(redisTemplate).delete("reset:" + token);
        verify(passwordEncoder).encode("NewPassword123!");
        verify(testUser).setMotDePasse("newHashedPassword");
        verify(userRepository).save(testUser);
    }

    @Test
    void resetPassword_tokenExpiredOrInvalid() {
        // Given
        String token = "invalidToken";
        ResetPasswordRequest req = new ResetPasswordRequest(token, "NewPassword123!");
        
        when(valueOps.get("reset:" + token)).thenReturn(null);

        // When & Then
        AppException exception = assertThrows(AppException.class, () -> authService.resetPassword(req));
        assertEquals(RESET_TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    void resetPassword_usageUnique() {
        // Given
        String token = "resetToken";
        ResetPasswordRequest req = new ResetPasswordRequest(token, "NewPassword123!");
        
        when(valueOps.get("reset:" + token)).thenReturn(testUserId.toString());
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("newHashedPassword");

        // When - Premier appel réussi
        assertDoesNotThrow(() -> authService.resetPassword(req));

        // When - Deuxième appel avec le même token
        when(valueOps.get("reset:" + token)).thenReturn(null); // Token déjà supprimé
        
        // Then - Deuxième appel doit échouer
        AppException exception = assertThrows(AppException.class, () -> authService.resetPassword(req));
        assertEquals(RESET_TOKEN_INVALID, exception.getErrorCode());
    }
}
