package com.ubs.orkestra.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Duration statistics for flows and pipelines")
public class DurationStatsDto {

    @Schema(description = "Flow ID")
    private Long flowId;

    @Schema(description = "Pipeline ID")
    private Long pipelineId;

    @Schema(description = "Entity name (flow name, pipeline name, etc.)")
    private String entityName;

    @Schema(description = "Average execution time in minutes")
    private Double averageExecutionTimeMinutes;

    @Schema(description = "Minimum execution time in minutes")
    private Double minExecutionTimeMinutes;

    @Schema(description = "Maximum execution time in minutes")
    private Double maxExecutionTimeMinutes;

    @Schema(description = "Total number of completed executions")
    private Long totalExecutions;

    // Constructors
    public DurationStatsDto() {}

    public DurationStatsDto(Long flowId, Long pipelineId, String entityName,
                          Double averageExecutionTimeMinutes, Double minExecutionTimeMinutes,
                          Double maxExecutionTimeMinutes, Long totalExecutions) {
        this.flowId = flowId;
        this.pipelineId = pipelineId;
        this.entityName = entityName;
        this.averageExecutionTimeMinutes = averageExecutionTimeMinutes;
        this.minExecutionTimeMinutes = minExecutionTimeMinutes;
        this.maxExecutionTimeMinutes = maxExecutionTimeMinutes;
        this.totalExecutions = totalExecutions;
    }

    // Getters and Setters
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

    public Double getAverageExecutionTimeMinutes() {
        return averageExecutionTimeMinutes;
    }

    public void setAverageExecutionTimeMinutes(Double averageExecutionTimeMinutes) {
        this.averageExecutionTimeMinutes = averageExecutionTimeMinutes;
    }

    public Double getMinExecutionTimeMinutes() {
        return minExecutionTimeMinutes;
    }

    public void setMinExecutionTimeMinutes(Double minExecutionTimeMinutes) {
        this.minExecutionTimeMinutes = minExecutionTimeMinutes;
    }

    public Double getMaxExecutionTimeMinutes() {
        return maxExecutionTimeMinutes;
    }

    public void setMaxExecutionTimeMinutes(Double maxExecutionTimeMinutes) {
        this.maxExecutionTimeMinutes = maxExecutionTimeMinutes;
    }

    public Long getTotalExecutions() {
        return totalExecutions;
    }

    public void setTotalExecutions(Long totalExecutions) {
        this.totalExecutions = totalExecutions;
    }
}