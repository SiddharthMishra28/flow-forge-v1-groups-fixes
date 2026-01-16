package com.ubs.orkestra.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Execution statistics grouped by application, flow, or pipeline")
public class ExecutionStatsDto {

    @Schema(description = "Application ID")
    private Long applicationId;

    @Schema(description = "Flow ID")
    private Long flowId;

    @Schema(description = "Pipeline ID")
    private Long pipelineId;

    @Schema(description = "Entity name (application name, flow name, etc.)")
    private String entityName;

    @Schema(description = "Total number of executions")
    private Long totalExecutions;

    @Schema(description = "Number of passed executions")
    private Long passedCount;

    @Schema(description = "Number of failed executions")
    private Long failedCount;

    @Schema(description = "Number of cancelled executions")
    private Long cancelledCount;

    @Schema(description = "Success rate percentage (0-100)")
    private Double successRate;

    // Constructors
    public ExecutionStatsDto() {}

    public ExecutionStatsDto(Long applicationId, Long flowId, Long pipelineId, String entityName,
                           Long totalExecutions, Long passedCount, Long failedCount, Long cancelledCount,
                           Double successRate) {
        this.applicationId = applicationId;
        this.flowId = flowId;
        this.pipelineId = pipelineId;
        this.entityName = entityName;
        this.totalExecutions = totalExecutions;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
        this.cancelledCount = cancelledCount;
        this.successRate = successRate;
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

    public Long getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(Long pipelineId) {
        this.pipelineId = pipelineId;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public Long getTotalExecutions() {
        return totalExecutions;
    }

    public void setTotalExecutions(Long totalExecutions) {
        this.totalExecutions = totalExecutions;
    }

    public Long getPassedCount() {
        return passedCount;
    }

    public void setPassedCount(Long passedCount) {
        this.passedCount = passedCount;
    }

    public Long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Long failedCount) {
        this.failedCount = failedCount;
    }

    public Long getCancelledCount() {
        return cancelledCount;
    }

    public void setCancelledCount(Long cancelledCount) {
        this.cancelledCount = cancelledCount;
    }

    public Double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(Double successRate) {
        this.successRate = successRate;
    }
}