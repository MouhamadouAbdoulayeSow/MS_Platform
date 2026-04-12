// Package: edu.projet.MS_Platform.auth.entity
package edu.projet.MS_Platform.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, length = 100)
    private String nom;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String motDePasse;  // haché BCrypt, jamais exposé
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    
    @Column(length = 10)
    private String niveau;  // L1 L2 L3 M1 M2, nullable
    
    private String avatar;  // URL, nullable
    
    @Column(nullable = false)
    private boolean actif = true;
    
    @Column(nullable = false)
    private boolean locked = false;  // OWASP A04 lockout
    
    @Column(nullable = false)
    private int failedAttempts = 0;  // OWASP A04
    
    private LocalDateTime lastLogin;  // nullable
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
