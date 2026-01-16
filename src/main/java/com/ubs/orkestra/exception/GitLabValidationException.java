package com.ubs.orkestra.exception;

public class GitLabValidationException extends RuntimeException {
    
    public GitLabValidationException(String message) {
        super(message);
    }
    
    public GitLabValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}