package com.twins.Tradeling.api;

import com.twins.Tradeling.api.ApiDtos.ApiError;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalidInput(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid JSON, enum value, date, or parameter type; see /api-docs", Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> business(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return response(status, status.name(), exception.getReason(), Map.of());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class, ArithmeticException.class})
    ResponseEntity<ApiError> conflict(Exception exception) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT", "Duplicate reference, conflicting update, or stock capacity exceeded; inventory changes were rolled back", Map.of());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message, fields));
    }
}
