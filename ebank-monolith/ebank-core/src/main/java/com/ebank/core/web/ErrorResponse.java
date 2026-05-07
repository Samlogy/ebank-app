package com.ebank.core.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final int status;
    private final String error;
    private final String message;
    private final String errorCode;
    private final String path;
    private final String method;
    private final LocalDateTime timestamp;
    private final String traceId;
    private final String stackTrace;
    private final List<ValidationError> validationErrors;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ValidationError {
        private final String field;
        private final String message;
        private final Object rejectedValue;
    }
}
