package com.meld.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A deliberately mapped API failure.
 *
 * <p>Services throw this instead of generic exceptions so the HTTP status and
 * machine-readable error code stay stable and are rendered through the single
 * error contract in {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
