package com.meld.common.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Centralized exception handling per RULES.md.
 * Never exposes stack traces, secrets, or password hashes.
 * Returns consistent error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> violations = new HashMap<>();
        ex.getConstraintViolations().forEach(v -> violations.put(v.getPropertyPath().toString(), v.getMessage()));
        return buildError(HttpStatus.BAD_REQUEST, "CONSTRAINT_VIOLATION", "Constraint validation failed", violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body is not valid JSON", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH",
                "Parameter '" + ex.getName() + "' has invalid type: " + ex.getValue(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage(), null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return buildError(HttpStatus.CONFLICT, "ILLEGAL_STATE", ex.getMessage(), null);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return buildError(ex.getStatus(), ex.getCode(), ex.getMessage(), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        // Never reveal whether the email exists or the password was wrong.
        return buildError(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Invalid email or password", null);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurity(SecurityException ex) {
        // Log full details server-side, return generic message to client
        log.warn("Security violation: {}", ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        // Log full stack trace server-side only
        log.error("Unhandled exception", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", null);
    }

    private ResponseEntity<ErrorResponse> buildError(HttpStatus status, String code, String message, Map<String, String> details) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        ErrorResponse error = new ErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                traceId,
                details
        );
        // Log error code and traceId for debugging (no stack trace in production logs)
        log.warn("Error {} [{}] {}: {}", code, traceId, status.value(), message);
        return ResponseEntity.status(status).body(error);
    }

    public record ErrorResponse(
            Instant timestamp,
            int status,
            String code,
            String message,
            String traceId,
            Map<String, String> details
    ) {
        public JsonNode toJson() {
            ObjectMapper localMapper = new ObjectMapper();
            ObjectNode node = localMapper.createObjectNode();
            node.put("timestamp", timestamp.toString());
            node.put("status", status);
            node.put("code", code);
            node.put("message", message);
            node.put("traceId", traceId);
            if (details != null && !details.isEmpty()) {
                ObjectNode detailsNode = localMapper.createObjectNode();
                details.forEach(detailsNode::put);
                node.set("details", detailsNode);
            }
            return node;
        }
    }
}