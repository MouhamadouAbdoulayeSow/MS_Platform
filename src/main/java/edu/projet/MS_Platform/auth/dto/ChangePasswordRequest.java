// Package: edu.projet.MS_Platform.auth.dto
package edu.projet.MS_Platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "Le mot de passe actuel est obligatoire")
    String currentPassword,
    
    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    String newPassword
) {}
