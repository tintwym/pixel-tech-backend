package com.shopping.cart.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", safeClientMessage(ex.getMessage())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", safeClientMessage(ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(err.getField(), fieldMessage(err));
        }
        String message = errors.values().stream().findFirst().orElse("Validation failed");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Custom messages are full sentences; Spring defaults ("must not be blank") need the field name. */
    private static String fieldMessage(FieldError err) {
        String msg = err.getDefaultMessage() == null ? "is invalid" : err.getDefaultMessage();
        return Character.isUpperCase(msg.charAt(0)) ? msg : err.getField() + " " + msg;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "A record with that value already exists"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        // Avoid leaking nested Stripe/DB details to clients.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "An unexpected error occurred"));
    }

    private static String safeClientMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Bad request";
        }
        // Keep messages short; strip nested causes that may include internals.
        int cut = message.indexOf(':');
        if (cut > 0 && message.toLowerCase().contains("exception")) {
            return "Bad request";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
