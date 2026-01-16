package com.ubs.orkestra.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Flow step data for create/update operations")
public class FlowStepCreateDto {

    @NotNull(message = "Application ID is required")
    @Schema(description = "Application ID", example = "1")
    private Long applicationId;

    @NotNull(message = "Branch is required")
    @NotBlank(message = "Branch cannot be blank")
    @Schema(description = "Git branch name", example = "main")
    private String branch;

    @NotNull(message = "Test tag is required")
    @NotBlank(message = "Test tag cannot be blank")
    @Schema(description = "Test tag identifier", example = "regression")
    private String testTag;

    @NotNull(message = "Test stage is required")
    @NotBlank(message = "Test stage cannot be blank")
    @Schema(description = "Test stage identifier", example = "development")
    private String testStage;

    @Schema(description = "Description of the flow step")
    private String description;

    @Schema(description = "List of Squash step IDs")
    private List<Long> squashStepIds;
    
    @Schema(description = "List of test data IDs to associate with this flow step")
    private List<Long> testData;
    
    @Valid
    @Schema(description = "Optional scheduler configuration for scheduling or delaying step execution")
    private InvokeSchedulerDto invokeScheduler;

    // Constructors
    public FlowStepCreateDto() {}

    public FlowStepCreateDto(Long applicationId, String branch, String testTag, String testStage, String description,
                            List<Long> squashStepIds, List<Long> testData, InvokeSchedulerDto invokeScheduler) {
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

    public List<Long> getTestData() {
        return testData;
    }

    public void setTestData(List<Long> testData) {
        this.testData = testData;
    }

    public InvokeSchedulerDto getInvokeScheduler() {
        return invokeScheduler;
    }

    public void setInvokeScheduler(InvokeSchedulerDto invokeScheduler) {
        this.invokeScheduler = invokeScheduler;
    }
}