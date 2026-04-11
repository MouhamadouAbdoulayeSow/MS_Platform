// Package: sn.lms.platform.shared.util

package edu.projet.MS_Platform.shared.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import edu.projet.MS_Platform.shared.exception.AppErrorCode;
import edu.projet.MS_Platform.shared.exception.AppException;

import java.util.UUID;

public class SecurityUtils {
    
    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(AppErrorCode.TOKEN_INVALID);
        }
        return UUID.fromString((String) auth.getPrincipal());
    }
    
    public static String getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(AppErrorCode.TOKEN_INVALID);
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(authority -> authority.replace("ROLE_", ""))
                .orElseThrow(() -> new AppException(AppErrorCode.TOKEN_INVALID));
    }
    
    public static boolean isAdmin() {
        return "ADMIN".equals(getCurrentUserRole());
    }
    
    public static void assertOwnershipOrAdmin(UUID ownerId) {
        if (!isAdmin() && !getCurrentUserId().equals(ownerId)) {
            throw new AppException(AppErrorCode.FORBIDDEN);
        }
    }
}
