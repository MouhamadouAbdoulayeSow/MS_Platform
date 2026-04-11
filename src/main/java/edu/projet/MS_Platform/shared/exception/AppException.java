// Package: sn.lms.platform.shared.exception

package edu.projet.MS_Platform.shared.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final AppErrorCode errorCode;
    private final String detail;
    
    public AppException(AppErrorCode errorCode) {
        super(errorCode.userMessage);
        this.errorCode = errorCode;
        this.detail = null;
    }
    
    public AppException(AppErrorCode errorCode, String detail) {
        super(errorCode.userMessage);
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
