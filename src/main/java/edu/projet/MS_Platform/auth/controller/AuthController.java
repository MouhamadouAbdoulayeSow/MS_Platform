// Package: edu.projet.MS_Platform.auth.controller
package edu.projet.MS_Platform.auth.controller;

import edu.projet.MS_Platform.auth.dto.*;
import edu.projet.MS_Platform.auth.service.AuthService;
import edu.projet.MS_Platform.shared.exception.AppException;
import edu.projet.MS_Platform.shared.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        AuthResponse response = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        AuthResponse response = authService.login(req, ip);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader,
                                       @Valid @RequestBody RefreshRequest req) {
        String accessToken = authHeader.substring(7); // Remove "Bearer "
        authService.logout(accessToken, req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        AuthResponse response = authService.refresh(req.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        try {
            authService.forgotPassword(req.email());
        } catch (AppException e) {
            // A07 - Ne jamais révéler si l'email existe, même en cas d'erreur
            // Logger l'erreur côté serveur mais retourner la même réponse
        }
        return ResponseEntity.ok(Map.of("message", "Si l'email existe, un lien a été envoyé."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé."));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> getMe() {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserProfileResponse response = authService.getMe(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserProfileResponse> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        UserProfileResponse response = authService.updateProfile(userId, req);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        authService.changePassword(userId, req);
        return ResponseEntity.noContent().build();
    }
}
