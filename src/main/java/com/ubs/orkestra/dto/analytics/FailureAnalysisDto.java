package com.ubs.orkestra.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Failure analysis data for top failing entities")
public class FailureAnalysisDto {

    @Schema(description = "Application ID")
    private Long applicationId;

    @Schema(description = "Flow ID")
    private Long flowId;

    @Schema(description = "Branch name")
    private String branch;

    @Schema(description = "Entity name (application name, flow name, branch name)")
    private String entityName;

    @Schema(description = "Entity type (APPLICATION, FLOW, BRANCH)")
    private String entityType;

    @Schema(description = "Total number of failures")
    private Long failureCount;

    @Schema(description = "Total number of executions")
    private Long totalExecutions;

    @Schema(description = "Failure rate percentage (0-100)")
    private Double failureRate;

    @Schema(description = "Most recent failure date")
    private String lastFailureDate;

    // Constructors
    public FailureAnalysisDto() {}

    public FailureAnalysisDto(Long applicationId, Long flowId, String branch, String entityName,
                            String entityType, Long failureCount, Long totalExecutions,
                            Double failureRate, String lastFailureDate) {
        this.applicationId = applicationId;
        this.flowId = flowId;
        this.branch = branch;
        this.entityName = entityName;
        this.entityType = entityType;
        this.failureCount = failureCount;
        this.totalExecutions = totalExecutions;
        this.failureRate = failureRate;
        this.lastFailureDate = lastFailureDate;
    }

    // Getters and Setters
    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getFlowId() {
        return flowId;
    }

    public void setFlowId(Long flowId) {
        this.flowId = flowId;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(Long failureCount) {
        this.failureCount = failureCount;
    }

    public Long getTotalExecutions() {
        return totalExecutions;
    }

    public void setTotalExecutions(Long totalExecutions) {
        this.totalExecutions = totalExecutions;
    }

    public Double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(Double failureRate) {
        this.failureRate = failureRate;
    }

    public String getLastFailureDate() {
        return lastFailureDate;
    }

    public void setLastFailureDate(String lastFailureDate) {
        this.lastFailureDate = lastFailureDate;
    }
}