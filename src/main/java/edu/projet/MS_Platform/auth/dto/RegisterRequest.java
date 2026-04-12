// Package: edu.projet.MS_Platform.auth.dto
package edu.projet.MS_Platform.auth.dto;

import edu.projet.MS_Platform.auth.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Le nom est obligatoire")
    @Size(min = 2, max = 100, message = "Le nom doit contenir entre 2 et 100 caractères")
    String nom,
    
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    String email,
    
    @NotBlank(message = "Le mot de passe est obligatoire")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,128}$",
             message = "Min 8 caractères, 1 majuscule, 1 chiffre, 1 caractère spécial")
    String motDePasse,
    
    Role role
) {}
