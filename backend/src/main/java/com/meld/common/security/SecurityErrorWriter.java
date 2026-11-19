package com.meld.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the same error contract used by GlobalExceptionHandler, from inside
 * the security filter chain where @RestControllerAdvice cannot run.
 */
final class SecurityErrorWriter {

    private SecurityErrorWriter() {}

    static void write(ObjectMapper objectMapper, HttpServletResponse response,
                      HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        body.put("traceId", UUID.randomUUID().toString().substring(0, 8));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
