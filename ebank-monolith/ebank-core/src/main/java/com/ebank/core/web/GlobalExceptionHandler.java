package com.ebank.core.web;

import com.ebank.core.exception.BusinessRuleViolationException;
import com.ebank.core.exception.ErrorHandlingProperties;
import com.ebank.core.exception.ResourceNotFoundException;
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
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorHandlingProperties properties;

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        log.warn("Resource not found: {} {}", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), ex.getErrorCode(), req, ex, null);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleBusinessRule(BusinessRuleViolationException ex, HttpServletRequest req) {
        log.warn("Business rule violation: {} {}", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), ex.getErrorCode(), req, ex, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        log.warn("Bad credentials at {}", req.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password", "BAD_CREDENTIALS", req, ex, null);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUsernameNotFound(UsernameNotFoundException ex, HttpServletRequest req) {
        log.warn("User not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), "USER_NOT_FOUND", req, ex, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErrorResponse.ValidationError> errors = null;
        if (properties.isIncludeValidationErrors()) {
            errors = ex.getBindingResult().getFieldErrors().stream()
                    .map(e -> ErrorResponse.ValidationError.builder()
                            .field(e.getField())
                            .message(e.getDefaultMessage())
                            .rejectedValue(properties.isShowDetails() ? e.getRejectedValue() : null)
                            .build())
                    .collect(Collectors.toList());
        }
        log.warn("Validation failed: {} {}", req.getMethod(), req.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "Validation Error", "Validation failed for one or more fields",
                "VALIDATION_ERROR", req, ex, errors);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleAll(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error: {} {}", req.getMethod(), req.getRequestURI(), ex);
        String message = properties.isShowDetails() ? ex.getMessage() : "An unexpected error occurred";
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", message, "INTERNAL_ERROR", req, ex, null);
    }

    private ErrorResponse build(HttpStatus status, String error, String message, String errorCode,
                                HttpServletRequest req, Exception ex,
                                List<ErrorResponse.ValidationError> validationErrors) {
        ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .path(req.getRequestURI())
                .method(req.getMethod())
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
