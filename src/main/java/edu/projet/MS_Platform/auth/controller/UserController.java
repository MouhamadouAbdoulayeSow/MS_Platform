// Package: edu.projet.MS_Platform.auth.controller
package edu.projet.MS_Platform.auth.controller;

import edu.projet.MS_Platform.auth.dto.*;
import edu.projet.MS_Platform.auth.entity.Role;
import edu.projet.MS_Platform.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserProfileResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Role roleFilter) {
        Page<UserProfileResponse> response = userService.listUsers(page, size, roleFilter);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSEUR')")
    public ResponseEntity<UserProfileResponse> getUserById(@PathVariable UUID id) {
        UserProfileResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProfileRequest req) {
        UserProfileResponse response = userService.updateUser(id, req);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest req) {
        userService.changeRole(id, req.role());
        // Retourner l'utilisateur mis à jour
        UserProfileResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest req) {
        userService.changeStatus(id, req.actif());
        // Retourner l'utilisateur mis à jour
        UserProfileResponse response = userService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/activity")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSEUR')")
    public ResponseEntity<UserActivityResponse> getUserActivity(@PathVariable UUID id) {
        UserActivityResponse response = userService.getUserActivity(id);
        return ResponseEntity.ok(response);
    }
}
