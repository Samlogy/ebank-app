package com.ebank.auth.exception;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "error.handling")
@Data
public class ErrorHandlingProperties {
    private boolean showDetails = false;
    private boolean includeStacktrace = false;
    private String traceIdHeader = "X-Trace-Id";
    private boolean includeValidationErrors = true;
    private boolean includeTraceId = true;
}
