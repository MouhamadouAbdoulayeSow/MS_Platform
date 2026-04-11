// Package: edu.projet.MS_Platform.auth.repository
package edu.projet.MS_Platform.auth.repository;

import edu.projet.MS_Platform.auth.entity.User;
import edu.projet.MS_Platform.auth.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    
    Optional<User> findByEmail(String email);
    
    boolean existsByEmail(String email);
    
    Page<User> findAllByRole(Role role, Pageable pageable);
    
    Page<User> findAll(Pageable pageable);
}
