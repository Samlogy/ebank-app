package com.ebank.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorHandlingProperties properties;

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {} {}, message: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), "RESOURCE_NOT_FOUND", request, ex, null);
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business rule violation: {} {}, message: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), "BUSINESS_RULE_VIOLATION", request, ex, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials at {}", request.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password", "BAD_CREDENTIALS", request, ex, null);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUsernameNotFound(UsernameNotFoundException ex, HttpServletRequest request) {
        log.warn("User not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), "USER_NOT_FOUND", request, ex, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.ValidationError> validationErrors = new ArrayList<>();

        if (properties.isIncludeValidationErrors()) {
            validationErrors = ex.getBindingResult().getFieldErrors().stream()
                    .map(e -> ErrorResponse.ValidationError.builder()
                            .field(e.getField())
                            .message(e.getDefaultMessage())
                            .rejectedValue(properties.isShowDetails() ? e.getRejectedValue() : null)
                            .build())
                    .collect(Collectors.toList());
        }

        log.warn("Validation failed: {} {}, errors: {}", request.getMethod(), request.getRequestURI(),
                validationErrors.stream().map(e -> e.getField() + ": " + e.getMessage()).collect(Collectors.joining(", ")));

        return build(HttpStatus.BAD_REQUEST, "Validation Error", "Validation failed for one or more fields",
                "VALIDATION_ERROR", request, ex, validationErrors);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleAll(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {} {}", request.getMethod(), request.getRequestURI(), ex);
        String message = properties.isShowDetails() ? ex.getMessage() : "An unexpected error occurred";
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", message, "INTERNAL_ERROR", request, ex, null);
    }

    private ErrorResponse build(HttpStatus status, String error, String message, String errorCode,
                                HttpServletRequest request, Exception ex,
                                List<ErrorResponse.ValidationError> validationErrors) {
        ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .validationErrors(validationErrors);

        if (properties.isIncludeTraceId()) {
            builder.traceId(MDC.get("traceId"));
        }

        if (properties.isIncludeStacktrace() && ex != null) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            builder.stackTrace(sw.toString());
        }

        return builder.build();
    }
}
