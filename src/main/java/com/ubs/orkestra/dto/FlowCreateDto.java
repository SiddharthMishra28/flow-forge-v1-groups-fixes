package com.ubs.orkestra.dto;

import com.ubs.orkestra.enums.AutomationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Flow data for create/update operations")
public class FlowCreateDto {

    @NotNull(message = "Flow steps are required")
    @NotEmpty(message = "Flow must have at least one step")
    @Valid
    @Schema(description = "List of flow steps with test data IDs")
    private List<FlowStepCreateDto> flowSteps;

    @NotNull(message = "Squash test case ID is required")
    @Schema(description = "Squash test case ID", example = "12345")
    private Long squashTestCaseId;

    @NotNull(message = "Squash test case is required")
    @Schema(description = "Squash test case name", example = "Login Test Case")
    private String squashTestCase;

    @Schema(description = "Automation status of the flow", example = "Automated", allowableValues = {"Automated", "Partial", "Not-Automated"})
    private AutomationStatus automationStatus;

    // Constructors
    public FlowCreateDto() {}

    public FlowCreateDto(List<FlowStepCreateDto> flowSteps, Long squashTestCaseId, String squashTestCase) {
        this.flowSteps = flowSteps;
        this.squashTestCaseId = squashTestCaseId;
        this.squashTestCase = squashTestCase;
    }

    public FlowCreateDto(List<FlowStepCreateDto> flowSteps, Long squashTestCaseId, String squashTestCase, AutomationStatus automationStatus) {
        this.flowSteps = flowSteps;
        this.squashTestCaseId = squashTestCaseId;
        this.squashTestCase = squashTestCase;
        this.automationStatus = automationStatus;
    }

    // Getters and Setters
    public List<FlowStepCreateDto> getFlowSteps() {
        return flowSteps;
    }

    public void setFlowSteps(List<FlowStepCreateDto> flowSteps) {
        this.flowSteps = flowSteps;
    }

    public Long getSquashTestCaseId() {
        return squashTestCaseId;
    }

    public void setSquashTestCaseId(Long squashTestCaseId) {
        this.squashTestCaseId = squashTestCaseId;
    }

    public String getSquashTestCase() {
        return squashTestCase;
    }

    public void setSquashTestCase(String squashTestCase) {
        this.squashTestCase = squashTestCase;
    }

    public AutomationStatus getAutomationStatus() {
        return automationStatus;
    }

    public void setAutomationStatus(AutomationStatus automationStatus) {
        this.automationStatus = automationStatus;
    }
}