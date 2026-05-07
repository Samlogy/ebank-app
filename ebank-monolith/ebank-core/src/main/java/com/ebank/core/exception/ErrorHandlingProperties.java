package com.ebank.core.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "error.handling")
public class ErrorHandlingProperties {
    private boolean includeStacktrace = false;
    private boolean includeTraceId = true;
    private boolean showDetails = false;
    private boolean includeValidationErrors = true;
}
