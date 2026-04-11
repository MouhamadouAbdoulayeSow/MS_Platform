// Package: sn.lms.platform.shared.exception

package edu.projet.MS_Platform.shared.exception;

public enum AppErrorCode {
    // Auth
    USER_NOT_FOUND(404, "Utilisateur introuvable"),
    USER_ALREADY_EXISTS(409, "Email déjà utilisé"),
    INVALID_CREDENTIALS(401, "Identifiants incorrects"),
    ACCOUNT_LOCKED(403, "Compte verrouillé. Contactez l'administrateur."),
    ACCOUNT_INACTIVE(403, "Compte inactif"),
    TOKEN_EXPIRED(401, "Token expiré"),
    TOKEN_INVALID(401, "Token invalide"),
    TOKEN_REVOKED(401, "Token révoqué"),
    TOO_MANY_REQUESTS(429, "Trop de tentatives. Réessayez dans 15 min."),
    RESET_TOKEN_INVALID(400, "Lien de réinitialisation invalide ou expiré"),
    RESET_TOKEN_ALREADY_USED(400, "Lien déjà utilisé"),
    
    // Cours
    COURSE_NOT_FOUND(404, "Cours introuvable"),
    MODULE_NOT_FOUND(404, "Module introuvable"),
    ALREADY_ENROLLED(409, "Déjà inscrit à ce cours"),
    NOT_ENROLLED(403, "Non inscrit à ce cours"),
    
    // Quiz
    QUIZ_NOT_FOUND(404, "Quiz introuvable"),
    MAX_ATTEMPTS_REACHED(403, "Nombre max de tentatives atteint"),
    ATTEMPT_NOT_FOUND(404, "Tentative introuvable"),
    ATTEMPT_ALREADY_SUBMITTED(409, "Tentative déjà soumise"),
    
    // Générique
    FORBIDDEN(403, "Accès refusé"),
    RESOURCE_NOT_FOUND(404, "Ressource introuvable"),
    VALIDATION_ERROR(400, "Erreur de validation"),
    INTERNAL_ERROR(500, "Erreur interne");
    
    public final int httpStatus;
    public final String userMessage;
    
    AppErrorCode(int httpStatus, String userMessage) {
        this.httpStatus = httpStatus;
        this.userMessage = userMessage;
    }
}
