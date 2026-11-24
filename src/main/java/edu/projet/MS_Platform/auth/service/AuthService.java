// Package: edu.projet.MS_Platform.auth.service
package edu.projet.MS_Platform.auth.service;

import edu.projet.MS_Platform.auth.dto.*;
import edu.projet.MS_Platform.auth.entity.Role;
import edu.projet.MS_Platform.auth.entity.User;
import edu.projet.MS_Platform.auth.mapper.UserMapper;
import edu.projet.MS_Platform.auth.repository.UserRepository;
import edu.projet.MS_Platform.shared.exception.AppException;
import edu.projet.MS_Platform.shared.security.JwtUtil;
import edu.projet.MS_Platform.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static edu.projet.MS_Platform.shared.exception.AppErrorCode.*;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserMapper userMapper;
    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private static final String DUMMY_HASH_PREFIX = "lms_dummy_timing";
    private String DUMMY_HASH;

    @PostConstruct
    private void init() {
        DUMMY_HASH = passwordEncoder.encode(DUMMY_HASH_PREFIX);
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        // A01 - Broken Access Control: Bloquer l'inscription ADMIN
        if (req.role() == Role.ADMIN) {
            throw new AppException(FORBIDDEN, "L'inscription en tant qu'ADMIN est interdite");
        }

        if (userRepository.existsByEmail(req.email())) {
            throw new AppException(USER_ALREADY_EXISTS, "Cet email est déjà utilisé");
        }

        User user = User.builder()
                .nom(req.nom())
                .email(req.email())
                .motDePasse(passwordEncoder.encode(req.motDePasse()))
                .role(req.role())
                .actif(true)
                .locked(false)
                .failedAttempts(0)
                .build();

        user = userRepository.save(user);
        log.info("REGISTER_SUCCESS userId={} email={}", user.getId(), user.getEmail());

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, String ip) {
        // A04 - Rate limiting par IP
        String ipKey = "rate:login:" + ip;
        ValueOperations<String, String> valueOps = redisTemplate.opsForValue();
        Long ipCount = valueOps.increment(ipKey);
        if (ipCount == 1L) {
            redisTemplate.expire(ipKey, 15, TimeUnit.MINUTES);
        }
        if (ipCount > 5) {
            throw new AppException(TOO_MANY_REQUESTS, "Trop de tentatives de connexion depuis cette IP");
        }

        // A04 - Rate limiting par email
        String emailKey = "lockout:" + req.email();
        Long emailCount = valueOps.increment(emailKey);
        if (emailCount == 1L) {
            redisTemplate.expire(emailKey, 15, TimeUnit.MINUTES);
        }

        // A04 + A07 - Protection timing attack
        Optional<User> userOpt = userRepository.findByEmail(req.email());
        String hashedPassword = userOpt.map(User::getMotDePasse).orElse(DUMMY_HASH);
        boolean match = passwordEncoder.matches(req.motDePasse(), hashedPassword);

        if (userOpt.isEmpty() || !match) {
            // Incrémenter failedAttempts pour l'utilisateur existant
            userOpt.ifPresent(user -> {
                user.setFailedAttempts(user.getFailedAttempts() + 1);
                if (user.getFailedAttempts() >= 5) {
                    user.setLocked(true);
                }
                userRepository.save(user);
            });
            log.warn("LOGIN_FAILED email={} ip={} reason=INVALID_CREDENTIALS", maskEmail(req.email()), ip);
            throw new AppException(INVALID_CREDENTIALS, "Email ou mot de passe incorrect");
        }

        User user = userOpt.get();
        
        // Vérifications de sécurité
        if (user.isLocked()) {
            log.warn("LOGIN_FAILED email={} ip={} reason=ACCOUNT_LOCKED", maskEmail(req.email()), ip);
            throw new AppException(ACCOUNT_LOCKED, "Compte verrouillé");
        }
        if (!user.isActif()) {
            log.warn("LOGIN_FAILED email={} ip={} reason=ACCOUNT_INACTIVE", maskEmail(req.email()), ip);
            throw new AppException(ACCOUNT_INACTIVE, "Compte inactif");
        }

        // Succès: reset failedAttempts, mettre à jour lastLogin
        user.setFailedAttempts(0);
        user.setLastLogin(LocalDateTime.now());
        user = userRepository.save(user);

        // Nettoyer les clés Redis rate limiting
        redisTemplate.delete(ipKey);
        redisTemplate.delete(emailKey);

        log.info("LOGIN_SUCCESS userId={} ip={}", user.getId(), ip);
        return generateAuthResponse(user);
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        String accessJti = jwtUtil.extractJti(accessToken);
        String refreshJti = jwtUtil.extractJti(refreshToken);

        // Blacklister les tokens
        long accessTtl = jwtUtil.getRemainingTtlMillis(accessToken);
        redisTemplate.opsForValue().set("blacklist:" + accessJti, "1", accessTtl, TimeUnit.MILLISECONDS);
        
        redisTemplate.opsForValue().set("blacklist:" + refreshJti, "1", 7L, TimeUnit.DAYS);
        
        // Supprimer le refresh token de Redis
        redisTemplate.delete("refresh:" + refreshJti);

        log.info("LOGOUT userId={}", SecurityUtils.getCurrentUserId());
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        // A07 - Rotation du refresh token
        try {
            String oldJti = jwtUtil.extractJti(refreshToken);
            String userId = jwtUtil.extractUserId(refreshToken);

            // Vérifier que le refresh token existe dans Redis
            String storedUserId = redisTemplate.opsForValue().get("refresh:" + oldJti);
            if (storedUserId == null || !storedUserId.equals(userId)) {
                throw new AppException(TOKEN_REVOKED, "Refresh token révoqué");
            }

            // Blacklister l'ancien JTI
            long remainingTtl = jwtUtil.getRemainingTtlMillis(refreshToken);
            redisTemplate.opsForValue().set("blacklist:" + oldJti, "1", remainingTtl, TimeUnit.MILLISECONDS);

            // Supprimer l'ancien refresh token
            redisTemplate.delete("refresh:" + oldJti);

            // Charger l'utilisateur
            User user = userRepository.findById(UUID.fromString(userId))
                    .orElseThrow(() -> new AppException(USER_NOT_FOUND, "Utilisateur non trouvé"));
            
            if (!user.isActif()) {
                throw new AppException(ACCOUNT_INACTIVE, "Compte inactif");
            }

            // Générer nouveaux tokens ; generateAuthResponse stocke déjà le nouveau refresh token dans Redis
            return generateAuthResponse(user);

        } catch (Exception e) {
            if (e instanceof AppException) {
                throw e;
            }
            throw new AppException(TOKEN_INVALID, "Token invalide");
        }
    }

    @Transactional
    public void forgotPassword(String email) {
        // A07 - Ne jamais révéler si l'email existe
        User user = userRepository.findByEmail(email).orElse(null);
        
        if (user != null) {
            String token = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set("reset:" + token, user.getId().toString(), 1L, TimeUnit.HOURS);

            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(email);
                message.setSubject("Réinitialisation du mot de passe");
                message.setText("Pour réinitialiser votre mot de passe, cliquez sur ce lien:\n" +
                        frontendUrl + "/reset-password?token=" + token);
                mailSender.send(message);
                log.info("PASSWORD_RESET_EMAIL_SENT email={}", maskEmail(email));
            } catch (Exception e) {
                log.error("Failed to send password reset email", e);
                // Ne pas lever d'exception pour ne pas révéler l'existence de l'email
            }
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        // Vérifier le token
        String userIdStr = redisTemplate.opsForValue().get("reset:" + req.token());
        if (userIdStr == null) {
            throw new AppException(RESET_TOKEN_INVALID, "Token invalide ou expiré");
        }

        // Supprimer le token immédiatement (usage unique)
        redisTemplate.delete("reset:" + req.token());

        // Charger l'utilisateur
        User user = userRepository.findById(UUID.fromString(userIdStr))
                .orElseThrow(() -> new AppException(USER_NOT_FOUND, "Utilisateur non trouvé"));

        // Mettre à jour le mot de passe
        user.setMotDePasse(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        // Invalider tous les refresh tokens de cet utilisateur
        invalidateAllUserRefreshTokens(user.getId().toString());

        log.info("PASSWORD_RESET userId={}", user.getId());
    }

    public UserProfileResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND, "Utilisateur non trouvé"));
        return userMapper.toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND, "Utilisateur non trouvé"));

        if (req.nom() != null) {
            user.setNom(req.nom());
        }
        if (req.avatar() != null) {
            // Validation simple d'URL
            if (!req.avatar().startsWith("http://") && !req.avatar().startsWith("https://")) {
                throw new AppException(VALIDATION_ERROR, "URL d'avatar invalide");
            }
            user.setAvatar(req.avatar());
        }
        if (req.niveau() != null) {
            user.setNiveau(req.niveau());
        }

        user = userRepository.save(user);
        return userMapper.toProfileResponse(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND, "Utilisateur non trouvé"));

        if (!passwordEncoder.matches(req.currentPassword(), user.getMotDePasse())) {
            throw new AppException(INVALID_CREDENTIALS, "Mot de passe actuel incorrect");
        }

        user.setMotDePasse(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        // Invalider tous les refresh tokens de cet utilisateur
        invalidateAllUserRefreshTokens(user.getId().toString());

        log.info("PASSWORD_CHANGED userId={}", user.getId());
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getRole().name(), user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        String jti = jwtUtil.extractJti(refreshToken);

        // Stocker le refresh token dans Redis
        redisTemplate.opsForValue().set("refresh:" + jti, user.getId().toString(), 7L, TimeUnit.DAYS);

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                900, // 15 minutes
                userMapper.toProfileResponse(user)
        );
    }

    private void invalidateAllUserRefreshTokens(String userId) {
        // Parcourir tous les refresh tokens et invalider ceux de cet utilisateur
        var keys = redisTemplate.keys("refresh:*");
        if (keys != null) {
            for (String key : keys) {
                String storedUserId = redisTemplate.opsForValue().get(key);
                if (userId.equals(storedUserId)) {
                    String jti = key.substring("refresh:".length());
                    redisTemplate.opsForValue().set("blacklist:" + jti, "1", 7L, TimeUnit.DAYS);
                    redisTemplate.delete(key);
                }
            }
        }
    }

    private String maskEmail(String email) {
        return email.replaceAll("(?<=.).(?=[^@]*@)", "*");
    }
}
