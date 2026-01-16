package com.ubs.orkestra.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.ubs.orkestra.enums.TokenStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "gitlab_project_id", nullable = false)
    private String gitlabProjectId;

    @NotBlank
    @Column(name = "personal_access_token", nullable = false)
    private String personalAccessToken;

    @NotBlank
    @Column(name = "application_name", nullable = false)
    private String applicationName;

    @NotBlank
    @Column(name = "application_description", nullable = false)
    private String applicationDescription;

    @Column(name = "project_name")
    private String projectName;

    @Column(name = "project_url")
    private String projectUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_status", nullable = false)
    private TokenStatus tokenStatus = TokenStatus.ACTIVE;

    @Column(name = "token_validation_last_update_date")
    private LocalDateTime tokenValidationLastUpdateDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public Application() {}

    public Application(String gitlabProjectId, String personalAccessToken) {
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
