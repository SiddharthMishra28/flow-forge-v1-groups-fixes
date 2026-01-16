package com.ubs.orkestra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

public class ValidationRequestDto {

    @NotNull(message = "Access token is required")
    @NotBlank(message = "Access token cannot be blank")
    @Schema(description = "GitLab personal access token", example = "glpat-xxxxxxxxxxxxxxxxxxxx")
    private String accessToken;

    @NotNull(message = "Project ID is required")
    @NotBlank(message = "Project ID cannot be blank")
    @Schema(description = "GitLab project ID", example = "12345")
    private String projectId;

    // Constructors
    public ValidationRequestDto() {}

    public ValidationRequestDto(String accessToken, String projectId) {
        this.accessToken = accessToken;
        this.projectId = projectId;
    }

    // Getters and Setters
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
}