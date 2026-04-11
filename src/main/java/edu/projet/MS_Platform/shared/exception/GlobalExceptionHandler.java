// Package: sn.lms.platform.shared.exception

package edu.projet.MS_Platform.shared.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    
    private final ObjectMapper objectMapper;
    
    @ExceptionHandler(AppException.class)
    public ProblemDetail handleAppException(AppException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.valueOf(ex.getErrorCode().httpStatus), 
            ex.getErrorCode().userMessage
        );
        problemDetail.setType(URI.create("https://lms-platform/errors/" + ex.getErrorCode().name().toLowerCase()));
        problemDetail.setTitle("Application Error");
        problemDetail.setProperty("errorCode", ex.getErrorCode().name());
        problemDetail.setProperty("timestamp", Instant.now());
        if (ex.getDetail() != null) {
            problemDetail.setProperty("detail", ex.getDetail());
        }
        return problemDetail;
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, 
            "Validation échouée"
        );
        problemDetail.setType(URI.create("https://lms-platform/errors/validation"));
        problemDetail.setTitle("Validation Error");
        problemDetail.setProperty("errorCode", "VALIDATION_ERROR");
        problemDetail.setProperty("fields", fieldErrors);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolationException(ConstraintViolationException ex, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        for (ConstraintViolation<?> violation : violations) {
            String fieldName = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            fieldErrors.put(fieldName, message);
        }
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, 
            "Validation échouée"
        );
        problemDetail.setType(URI.create("https://lms-platform/errors/validation"));
        problemDetail.setTitle("Validation Error");
        problemDetail.setProperty("errorCode", "VALIDATION_ERROR");
        problemDetail.setProperty("fields", fieldErrors);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, 
            AppErrorCode.FORBIDDEN.userMessage
        );
        problemDetail.setType(URI.create("https://lms-platform/errors/forbidden"));
        problemDetail.setTitle("Access Denied");
        problemDetail.setProperty("errorCode", AppErrorCode.FORBIDDEN.name());
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, 
            "Authentification requise"
        );
        problemDetail.setType(URI.create("https://lms-platform/errors/unauthorized"));
        problemDetail.setTitle("Authentication Failed");
        problemDetail.setProperty("errorCode", "UNAUTHORIZED");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, 
            "Corps de requête invalide"
        );
        problemDetail.setType(URI.create("https://lms-platform/errors/message-not-readable"));
        problemDetail.setTitle("Invalid Request Body");
        problemDetail.setProperty("errorCode", "INVALID_REQUEST_BODY");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
    
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, WebRequest request) {
        log.error("Unexpected error occurred", ex);
        
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, 
            AppErrorCode.INTERNAL_ERROR.userMessage
        );
        problemDetail.setType(URI.create("https://lms-platform/errors/internal"));
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("errorCode", AppErrorCode.INTERNAL_ERROR.name());
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
