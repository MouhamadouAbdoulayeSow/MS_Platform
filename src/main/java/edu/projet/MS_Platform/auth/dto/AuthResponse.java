// Package: edu.projet.MS_Platform.auth.dto
package edu.projet.MS_Platform.auth.dto;

import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,  // "Bearer"
    long expiresIn,    // 900
    UserProfileResponse user
) {}
