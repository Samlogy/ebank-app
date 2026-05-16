package com.ebank.transactions.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

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

    @ExceptionHandler(TransactionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleTransactionNotFound(
            TransactionNotFoundException ex,
            ServerHttpRequest request) {

        log.warn("Transaction not found: {} {}, message: {}",
                request.getMethod(), request.getURI().getPath(), ex.getMessage());

        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(),
                "TRANSACTION_NOT_FOUND", request, ex, null);
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleBusinessException(
            BusinessException ex,
            ServerHttpRequest request) {

        log.warn("Business rule violation: {} {}, message: {}",
                request.getMethod(), request.getURI().getPath(), ex.getMessage());

        return buildErrorResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(),
                "BUSINESS_RULE_VIOLATION", request, ex, null);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationExceptions(
            WebExchangeBindException ex,
            ServerHttpRequest request) {

        List<ErrorResponse.ValidationError> validationErrors = new ArrayList<>();

        if (properties.isIncludeValidationErrors()) {
            validationErrors = ex.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(error -> ErrorResponse.ValidationError.builder()
                            .field(error.getField())
                            .message(error.getDefaultMessage())
                            .rejectedValue(properties.isShowDetails() ? error.getRejectedValue() : null)
                            .build())
                    .collect(Collectors.toList());
        }

        log.warn("Validation failed: {} {}, errors: {}",
                request.getMethod(),
                request.getURI().getPath(),
                validationErrors.stream()
                        .map(e -> e.getField() + ": " + e.getMessage())
                        .collect(Collectors.joining(", ")));

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation Error",
                "Validation failed for one or more fields",
                "VALIDATION_ERROR", request, ex, validationErrors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(
            IllegalArgumentException ex,
            ServerHttpRequest request) {

        log.warn("Illegal argument: {} {}, message: {}",
                request.getMethod(), request.getURI().getPath(), ex.getMessage());

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(),
                "BAD_REQUEST", request, ex, null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleAllExceptions(
            Exception ex,
            ServerHttpRequest request) {

        log.error("Unexpected error: {} {}",
                request.getMethod(), request.getURI().getPath(), ex);

        String message = properties.isShowDetails() ? ex.getMessage() : "An unexpected error occurred";

        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                message, "INTERNAL_ERROR", request, ex, null);
    }

    private ErrorResponse buildErrorResponse(
            HttpStatus status,
            String error,
            String message,
            String errorCode,
            ServerHttpRequest request,
            Exception ex,
            List<ErrorResponse.ValidationError> validationErrors) {

        ErrorResponse.ErrorResponseBuilder builder = ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .errorCode(errorCode)
                .timestamp(LocalDateTime.now())
                .path(request.getURI().getPath())
                .method(request.getMethod().name())
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
