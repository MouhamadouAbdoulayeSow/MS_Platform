// Package: edu.projet.MS_Platform.auth.dto
package edu.projet.MS_Platform.auth.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
    String nom,  // nullable
    
    String avatar,  // nullable, validé URL en service
    
    String niveau  // nullable
) {}
