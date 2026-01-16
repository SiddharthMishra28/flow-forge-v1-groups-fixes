package com.ubs.orkestra.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class ValidationResponseDto {

    @Schema(description = "Validation status", example = "true")
    private boolean valid;

    @Schema(description = "Validation message", example = "GitLab connection successful")
    private String message;

    @Schema(description = "GitLab project name (if validation successful)", example = "my-awesome-project")
    private String projectName;

    @Schema(description = "GitLab project URL (if validation successful)", example = "https://gitlab.com/user/my-awesome-project")
    private String projectUrl;

    // Constructors
    public ValidationResponseDto() {}

    public ValidationResponseDto(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public ValidationResponseDto(boolean valid, String message, String projectName, String projectUrl) {
        this.valid = valid;
        this.message = message;
        this.projectName = projectName;
        this.projectUrl = projectUrl;
    }

    // Getters and Setters
    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectUrl() {
        return projectUrl;
    }

    public void setProjectUrl(String projectUrl) {
        this.projectUrl = projectUrl;
    }
}