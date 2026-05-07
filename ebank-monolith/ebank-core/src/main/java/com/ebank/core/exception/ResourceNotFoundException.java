package com.ebank.core.exception;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(resource + " not found with " + field + " = " + value, "RESOURCE_NOT_FOUND");
    }

    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND");
    }
}
