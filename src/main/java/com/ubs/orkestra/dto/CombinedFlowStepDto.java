package com.ubs.orkestra.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class CombinedFlowStepDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Auto-generated unique identifier")
    private Long id;

    @NotNull(message = "Application ID is required")
    private Long applicationId;

    @NotNull(message = "Branch is required")
    @NotBlank(message = "Branch cannot be blank")
    private String branch;

    @NotNull(message = "Test tag is required")
    @NotBlank(message = "Test tag cannot be blank")
    private String testTag;

    @NotNull(message = "Test stage is required")
    @NotBlank(message = "Test stage cannot be blank")
    private String testStage;

    @Schema(description = "Description of the flow step")
    private String description;

    private List<Long> squashStepIds;
    
    @Valid
    @Schema(description = "List of test data objects")
    private List<TestDataDto> testData;
    
    @Valid
    @Schema(description = "Optional scheduler configuration for scheduling or delaying step execution")
    private InvokeSchedulerDto invokeScheduler;
    
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Timestamp when the record was created")
    private LocalDateTime createdAt;
    
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Timestamp when the record was last updated")
    private LocalDateTime updatedAt;

    // Constructors
    public CombinedFlowStepDto() {}

    public CombinedFlowStepDto(Long applicationId, String branch, String testTag, String testStage, String description,
                              List<Long> squashStepIds, List<TestDataDto> testData, InvokeSchedulerDto invokeScheduler) {
        this.applicationId = applicationId;
        this.branch = branch;
        this.testTag = testTag;
        this.testStage = testStage;
        this.description = description;
        this.squashStepIds = squashStepIds;
        this.testData = testData;
        this.invokeScheduler = invokeScheduler;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getTestTag() {
        return testTag;
    }

    public void setTestTag(String testTag) {
        this.testTag = testTag;
    }

    public String getTestStage() {
        return testStage;
    }

    public void setTestStage(String testStage) {
        this.testStage = testStage;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Long> getSquashStepIds() {
        return squashStepIds;
    }

    public void setSquashStepIds(List<Long> squashStepIds) {
        this.squashStepIds = squashStepIds;
    }

    public List<TestDataDto> getTestData() {
        return testData;
    }

    public void setTestData(List<TestDataDto> testData) {
        this.testData = testData;
    }

    public InvokeSchedulerDto getInvokeScheduler() {
        return invokeScheduler;
    }

    public void setInvokeScheduler(InvokeSchedulerDto invokeScheduler) {
        this.invokeScheduler = invokeScheduler;
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
