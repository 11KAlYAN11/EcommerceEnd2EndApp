package com.ecommerce.common.exception;

import com.ecommerce.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized error handling for all controllers.
 *
 * @RestControllerAdvice: applies to ALL @RestController classes.
 * When any controller method throws an exception, Spring routes it here
 * before sending a response — one place handles all errors.
 *
 * Without this: Spring returns a generic 500 with HTML error page.
 * With this: we return our standard ApiResponse with a clear message.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Handles @Valid validation failures (blank email, short password, etc.)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .build());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }

    /**
     * Phase 15 — AccessDeniedException handler.
     *
     * WHY THIS IS NEEDED:
     *   Without this handler, @PreAuthorize failures return 403 with Spring's
     *   default HTML error page or empty body — not our standard ApiResponse.
     *
     *   With this: all 403s follow the same {success, message} structure.
     *
     * AccessDeniedException vs AuthenticationException:
     *   AuthenticationException → user is NOT authenticated (no token, expired) → 401
     *   AccessDeniedException   → user IS authenticated but lacks the role → 403
     *   Example: logged-in ROLE_USER calls DELETE /api/products → AccessDeniedException
     *
     * NOTE: Spring Security catches AccessDeniedException BEFORE it reaches
     *   this handler IF you're not authenticated. This handler only fires
     *   for authenticated users who don't have the required role.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied: you don't have permission to perform this action"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * WHY THIS IS NEEDED:
     * Category.name has @Column(unique=true). If you create "Electronics" twice,
     * Hibernate throws DataIntegrityViolationException (DB unique constraint violation).
     * Same for any other unique/not-null DB constraint.
     * Without this handler: 500 "An unexpected error occurred".
     * With this handler: 409 Conflict with a clear message.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        String message = extractConstraintMessage(ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(message));
    }

    // Malformed JSON body — e.g. missing quotes, trailing comma
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Request body is malformed or missing"));
    }

    // Required query param missing — e.g. ?from= missing when mandatory
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Missing required parameter: " + ex.getParameterName()));
    }

    // Type mismatch — e.g. ?page=abc instead of ?page=0
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid value for parameter '" + ex.getName() + "': " + ex.getValue()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }

    private String extractConstraintMessage(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        if (cause != null) {
            // PostgreSQL unique constraint message: "Key (name)=(Electronics) already exists"
            if (cause.contains("unique constraint") || cause.contains("Unique index") || cause.contains("already exists")) {
                return "A record with this value already exists (duplicate entry)";
            }
            // Not-null constraint
            if (cause.contains("null value") || cause.contains("cannot be null")) {
                return "A required field is missing or null";
            }
        }
        return "Data conflict: the operation violates a database constraint";
    }
}
