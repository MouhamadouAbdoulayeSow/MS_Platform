// Package: edu.projet.MS_Platform.auth.service
package edu.projet.MS_Platform.auth.service;

import edu.projet.MS_Platform.auth.dto.*;
import edu.projet.MS_Platform.auth.entity.Role;
import edu.projet.MS_Platform.auth.entity.User;
import edu.projet.MS_Platform.auth.mapper.UserMapper;
import edu.projet.MS_Platform.auth.repository.UserRepository;
import edu.projet.MS_Platform.shared.exception.AppException;
import edu.projet.MS_Platform.shared.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static edu.projet.MS_Platform.shared.exception.AppErrorCode.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public Page<UserProfileResponse> listUsers(int page, int size, Role roleFilter) {
        // A01 - Access control: uniquement accessible par ADMIN (vérifié dans controller)
        Pageable pageable = PageRequest.of(page, size);
        if (roleFilter != null) {
            return userRepository.findAllByRole(roleFilter, pageable)
                    .map(userMapper::toProfileResponse);
        }
        return userRepository.findAll(pageable)
                .map(userMapper::toProfileResponse);
    }

    public UserProfileResponse getUserById(UUID id) {
        // A01 - Access control: ADMIN ou PROFESSEUR (vérifié dans controller)
        // PROFESSEUR ne peut voir que les ETUDIANT
        User currentUser = getCurrentUser();
        if (currentUser.getRole() == Role.PROFESSEUR) {
            User targetUser = userRepository.findById(id)
                    .orElseThrow(() -> new AppException(USER_NOT_FOUND, "Utilisateur non trouvé"));
            if (targetUser.getRole() != Role.ETUDIANT) {
                throw new AppException(FORBIDDEN, "Accès interdit");
            }
            return userMapper.toProfileResponse(targetUser);
        }
        
        // ADMIN peut voir tout le monde
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND, "Utilisateur non trouvé"));
        return userMapper.toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateUser(UUID id, UpdateProfileRequest req) {
        // A01 - Access control: uniquement ADMIN (vérifié dans controller)
        // Pour la mise à jour par l'utilisateur lui-même, voir AuthService.updateProfile
        User user = userRepository.findById(id)
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
        log.info("USER_UPDATED_ADMIN adminId={} targetId={}", getCurrentUser().getId(), id);
        return userMapper.toProfileResponse(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        // A01 - Access control: uniquement ADMIN (vérifié dans controller)
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND, "Utilisateur non trouvé"));
        
        // Empêcher la suppression d'un autre ADMIN
        if (user.getRole() == Role.ADMIN) {
            User currentUser = getCurrentUser();
            if (!currentUser.getId().equals(id)) {
                throw new AppException(FORBIDDEN, "Impossible de supprimer un autre administrateur");
            }
        }
        
        // Soft delete
        user.setActif(false);
        userRepository.save(user);
        log.info("USER_DELETED adminId={} targetId={}", getCurrentUser().getId(), id);
    }

    @Transactional
    public void changeRole(UUID id, Role newRole) {
        // A01 - Access control: uniquement ADMIN (vérifié dans controller)
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND, "Utilisateur non trouvé"));
        
        // Empêcher la modification du rôle d'un autre ADMIN
        if (user.getRole() == Role.ADMIN) {
            User currentUser = getCurrentUser();
            if (!currentUser.getId().equals(id)) {
                throw new AppException(FORBIDDEN, "Impossible de modifier le rôle d'un autre administrateur");
            }
        }
        
        // Empêcher la promotion en ADMIN via cette méthode
        if (newRole == Role.ADMIN) {
            throw new AppException(FORBIDDEN, "La promotion en administrateur est interdite via cette méthode");
        }
        
        user.setRole(newRole);
        userRepository.save(user);
        log.info("ROLE_CHANGED adminId={} targetId={} newRole={}", getCurrentUser().getId(), id, newRole);
    }

    @Transactional
    public void changeStatus(UUID id, boolean actif) {
        // A01 - Access control: uniquement ADMIN (vérifié dans controller)
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND, "Utilisateur non trouvé"));
        
        // Empêcher la désactivation d'un autre ADMIN
        if (user.getRole() == Role.ADMIN && !actif) {
            User currentUser = getCurrentUser();
            if (!currentUser.getId().equals(id)) {
                throw new AppException(FORBIDDEN, "Impossible de désactiver un autre administrateur");
            }
        }
        
        user.setActif(actif);
        userRepository.save(user);
        log.info("STATUS_CHANGED adminId={} targetId={} actif={}", getCurrentUser().getId(), id, actif);
    }

    public UserActivityResponse getUserActivity(UUID id) {
        // A01 - Access control: ADMIN ou PROFESSEUR (vérifié dans controller)
        // PROFESSEUR ne peut voir que les ETUDIANT
        User currentUser = getCurrentUser();
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND, "Utilisateur non trouvé"));
        
        if (currentUser.getRole() == Role.PROFESSEUR && targetUser.getRole() != Role.ETUDIANT) {
            throw new AppException(FORBIDDEN, "Accès interdit");
        }
        
        // Simulation de données d'activité (à implémenter selon les besoins métier)
        long connexions = targetUser.getLastLogin() != null ? 1 : 0;
        return new UserActivityResponse(
                connexions,
                targetUser.getLastLogin(),
                0L, // coursActifs - à implémenter
                0L  // quizPasses - à implémenter
        );
    }

    private User getCurrentUser() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(USER_NOT_FOUND, "Utilisateur courant non trouvé"));
    }
}
