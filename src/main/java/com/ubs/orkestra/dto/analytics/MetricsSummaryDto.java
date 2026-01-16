package com.ubs.orkestra.dto.analytics;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Overall execution metrics summary")
public class MetricsSummaryDto {

    @Schema(description = "Total number of flow executions")
    private Long totalFlowExecutions;

    @Schema(description = "Total number of pipeline executions")
    private Long totalPipelineExecutions;

    @Schema(description = "Number of passed executions")
    private Long passedCount;

    @Schema(description = "Number of failed executions")
    private Long failedCount;

    @Schema(description = "Number of cancelled executions")
    private Long cancelledCount;

    @Schema(description = "Success rate percentage (0-100)")
    private Double successRate;

    @Schema(description = "Failure rate percentage (0-100)")
    private Double failureRate;

    @Schema(description = "Average execution time in minutes")
    private Double averageExecutionTimeMinutes;

    // Constructors
    public MetricsSummaryDto() {}

    public MetricsSummaryDto(Long totalFlowExecutions, Long totalPipelineExecutions, 
                           Long passedCount, Long failedCount, Long cancelledCount,
                           Double successRate, Double failureRate, Double averageExecutionTimeMinutes) {
        this.totalFlowExecutions = totalFlowExecutions;
        this.totalPipelineExecutions = totalPipelineExecutions;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
        this.cancelledCount = cancelledCount;
        this.successRate = successRate;
        this.failureRate = failureRate;
        this.averageExecutionTimeMinutes = averageExecutionTimeMinutes;
    }

    // Getters and Setters
    public Long getTotalFlowExecutions() {
        return totalFlowExecutions;
    }

    public void setTotalFlowExecutions(Long totalFlowExecutions) {
        this.totalFlowExecutions = totalFlowExecutions;
    }

    public Long getTotalPipelineExecutions() {
        return totalPipelineExecutions;
    }

    public void setTotalPipelineExecutions(Long totalPipelineExecutions) {
        this.totalPipelineExecutions = totalPipelineExecutions;
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

    public Double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(Double failureRate) {
        this.failureRate = failureRate;
    }

    public Double getAverageExecutionTimeMinutes() {
        return averageExecutionTimeMinutes;
    }

    public void setAverageExecutionTimeMinutes(Double averageExecutionTimeMinutes) {
        this.averageExecutionTimeMinutes = averageExecutionTimeMinutes;
    }
}