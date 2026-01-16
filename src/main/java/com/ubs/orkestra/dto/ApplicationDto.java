package com.ubs.orkestra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ubs.orkestra.enums.TokenStatus;

import java.time.LocalDateTime;

public class ApplicationDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Auto-generated unique identifier")
    private Long id;

    @NotNull(message = "GitLab project ID is required")
    @NotBlank(message = "GitLab project ID cannot be blank")
    private String gitlabProjectId;

    @NotNull(message = "Personal access token is required")
    @NotBlank(message = "Personal access token cannot be blank")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, description = "Personal access token (write-only)")
    private String personalAccessToken;

    @NotNull(message = "Application is required")
    @NotBlank(message = "Application cannot be blank")
    private String applicationName;

    @NotNull(message = "Application is required")
    @NotBlank(message = "Application cannot be blank")
    private String applicationDescription;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "GitLab project name")
    private String projectName;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "GitLab project URL")
    private String projectUrl;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Token validation status")
    private TokenStatus tokenStatus;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Timestamp when token validation was last performed")
    private LocalDateTime tokenValidationLastUpdateDate;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Timestamp when the record was created")
    private LocalDateTime createdAt;
    
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Timestamp when the record was last updated")
    private LocalDateTime updatedAt;

    // Constructors
    public ApplicationDto() {}

    public ApplicationDto(String gitlabProjectId, String personalAccessToken) {
        this.gitlabProjectId = gitlabProjectId;
        this.personalAccessToken = personalAccessToken;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGitlabProjectId() {
        return gitlabProjectId;
    }

    public void setGitlabProjectId(String gitlabProjectId) {
        this.gitlabProjectId = gitlabProjectId;
    }

    public String getPersonalAccessToken() {
        return personalAccessToken;
    }

    public void setPersonalAccessToken(String personalAccessToken) {
        this.personalAccessToken = personalAccessToken;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationDescription() {
        return applicationDescription;
    }

    public void setApplicationDescription(String applicationDescription) {
        this.applicationDescription = applicationDescription;
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

    public TokenStatus getTokenStatus() {
        return tokenStatus;
    }

    public void setTokenStatus(TokenStatus tokenStatus) {
        this.tokenStatus = tokenStatus;
    }

    public LocalDateTime getTokenValidationLastUpdateDate() {
        return tokenValidationLastUpdateDate;
    }

    public void setTokenValidationLastUpdateDate(LocalDateTime tokenValidationLastUpdateDate) {
        this.tokenValidationLastUpdateDate = tokenValidationLastUpdateDate;
    }
}
