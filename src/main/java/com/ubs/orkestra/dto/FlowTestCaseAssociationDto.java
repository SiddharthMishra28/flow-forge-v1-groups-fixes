package com.ubs.orkestra.dto;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "DTO for associating/disassociating SquashTM test cases with flows")
public class FlowTestCaseAssociationDto {

    @NotNull(message = "SquashTM test case ID is required")
    @Schema(description = "SquashTM test case ID", example = "456", required = true)
    private Long squashTestCaseId;

    @Schema(description = "Optional description for the association", example = "Login flow test case")
    private String description;

    // Constructors
    public FlowTestCaseAssociationDto() {}

    public FlowTestCaseAssociationDto(Long squashTestCaseId, String description) {
        this.squashTestCaseId = squashTestCaseId;
        this.description = description;
    }

    // Getters and Setters
    public Long getSquashTestCaseId() {
        return squashTestCaseId;
    }

    public void setSquashTestCaseId(Long squashTestCaseId) {
        this.squashTestCaseId = squashTestCaseId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}