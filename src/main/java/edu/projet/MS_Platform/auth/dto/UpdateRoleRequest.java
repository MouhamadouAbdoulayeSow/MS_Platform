// Package: edu.projet.MS_Platform.auth.dto
package edu.projet.MS_Platform.auth.dto;

import edu.projet.MS_Platform.auth.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
    @NotNull(message = "Le rôle est obligatoire")
    Role role
) {}
