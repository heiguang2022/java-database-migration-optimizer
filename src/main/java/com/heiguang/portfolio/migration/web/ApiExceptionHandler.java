package com.heiguang.portfolio.migration.web;

import com.heiguang.portfolio.migration.migration.MigrationService.MigrationNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MigrationNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, Object> notFound(RuntimeException ex) { return error(404, ex.getMessage()); }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> badRequest(Exception ex) { return error(400, "Invalid request: " + ex.getMessage()); }

    private Map<String, Object> error(int status, String message) {
        return Map.of("timestamp", Instant.now().toString(), "status", status, "message", message);
    }
}
