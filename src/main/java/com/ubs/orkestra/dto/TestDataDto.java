package com.ubs.orkestra.dto;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

public class TestDataDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Auto-generated unique identifier")
    private Long dataId;

    @NotNull(message = "Application ID is required")
    private Long applicationId;

    @NotNull(message = "Application Name is required")
    private String applicationName;

    @NotNull(message = "Category is required")
    private String category;

    private String description;

    @NotNull(message = "Test data is required")
    private Map<String, String> variables;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Timestamp when the record was created")
    private LocalDateTime createdAt;
    
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Timestamp when the record was last updated")
    private LocalDateTime updatedAt;

    // Constructors
    public TestDataDto() {}

    public TestDataDto(Long applicationId, String applicationName, String category, String description, Map<String, String> variables) {
        this.applicationId = applicationId;
        this.applicationName = applicationName;
        this.category = category;
        this.description = description;
        this.variables = variables;
    }

    // Getters and Setters
    public Long getDataId() {
        return dataId;
    }

    public void setDataId(Long dataId) {
        this.dataId = dataId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
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
}