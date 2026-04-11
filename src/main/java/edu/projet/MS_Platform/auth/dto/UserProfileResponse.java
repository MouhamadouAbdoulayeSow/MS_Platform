// Package: edu.projet.MS_Platform.auth.dto
package edu.projet.MS_Platform.auth.dto;

import edu.projet.MS_Platform.auth.entity.Role;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String nom,
    String email,
    Role role,
    String niveau,
    String avatar,
    boolean actif,
    LocalDateTime lastLogin,
    LocalDateTime createdAt
    // JAMAIS motDePasse, failedAttempts, locked
) {}
